package com.toolshed.axon

import com.toolshed.race.ProbeClaim
import com.toolshed.race.ProbeClaimed
import com.toolshed.web.CommandDispatcher
import com.toolshed.web.ProbeRetryFlaky
import com.toolshed.web.RetryProbeHandlers
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.EventMessage
import org.axonframework.messaging.eventhandling.conversion.EventConverter
import org.axonframework.messaging.eventstreaming.StreamableEventSource
import org.axonframework.messaging.eventstreaming.StreamingCondition
import org.axonframework.messaging.eventstreaming.TrackingTokenSource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** The internal reaction dispatcher over real Axon Server: race re-decides, a refusal is an outcome, a failure gives up. */
@SpringBootTest
@Testcontainers
@ActiveProfiles("probes")
class ReactionDispatcherOverAxonServerTest {
    companion object {
        @Container
        @JvmStatic
        val axonServer = AxonServerContainer().withDcbContext(true)

        @DynamicPropertySource
        @JvmStatic
        fun axonServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("axon.axonserver.servers") { axonServer.axonServerAddress }
        }
    }

    @Autowired lateinit var reactions: ReactionDispatcher

    @Autowired lateinit var dispatcher: CommandDispatcher

    @Autowired lateinit var handlers: RetryProbeHandlers

    @Autowired lateinit var unitOfWorkFactory: UnitOfWorkFactory

    @Autowired lateinit var eventSource: StreamableEventSource

    @Autowired lateinit var tokenSource: TrackingTokenSource

    @Autowired lateinit var eventConverter: EventConverter

    /** Stored ProbeClaimed events for [claim], read from the start of the store over a short window. */
    private fun storedClaims(claim: UUID): Int {
        val seen = mutableListOf<EventMessage>()
        val deadline = Instant.now().plus(Duration.ofSeconds(3))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { seen.add(it.message()) }
                    }
                    stream.close()
                }
            }.join()
        return seen
            .filter { it.type().name() == ProbeClaimed::class.java.name }
            .map { checkNotNull(eventConverter.convertPayload<EventMessage, ProbeClaimed>(it, ProbeClaimed::class.java)) }
            .count { it.claimId == claim }
    }

    @Test
    fun `a reaction racing a REST command re-decides, leaving one winner and one event`() {
        repeat(10) {
            val claim = UUID.randomUUID()
            val pool = Executors.newFixedThreadPool(2)
            try {
                val rest =
                    Callable {
                        runCatching { dispatcher.send(ProbeClaim(claim), Any::class.java) }
                            .fold({ "accepted" }, { "refused-rest" })
                    }
                val reaction =
                    Callable {
                        when (val outcome = reactions.send(ProbeClaim(claim))) {
                            is ReactionOutcome.Accepted -> "accepted"
                            is ReactionOutcome.Refused -> "refused-reaction:${outcome.reason}"
                        }
                    }
                val outcomes = pool.invokeAll(listOf(rest, reaction)).map { it.get() }
                assertEquals(1, outcomes.count { it == "accepted" }, "outcomes: $outcomes")
                assertEquals(1, storedClaims(claim), "stored claims for $claim")
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun `a refusal comes back as an outcome, not an exception`() {
        val claim = UUID.randomUUID()
        assertIs<ReactionOutcome.Accepted>(reactions.send(ProbeClaim(claim)))
        val refused = reactions.send(ProbeClaim(claim))
        assertIs<ReactionOutcome.Refused>(refused)
        assertEquals("probe-claimed", refused.reason)
        assertEquals(1, storedClaims(claim))
    }

    @Test
    fun `a persistent server failure gives up after three attempts`() {
        handlers.flakyAttempts.set(0)
        assertFailsWith<Exception> { reactions.send(ProbeRetryFlaky("x")) }
        assertEquals(3, handlers.flakyAttempts.get())
    }
}
