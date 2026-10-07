package com.toolshed.race

import com.toolshed.testsupport.SharedAxonServer
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.EventMessage
import org.axonframework.messaging.eventhandling.conversion.EventConverter
import org.axonframework.messaging.eventstreaming.StreamableEventSource
import org.axonframework.messaging.eventstreaming.StreamingCondition
import org.axonframework.messaging.eventstreaming.TrackingTokenSource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals

/** Two concurrent conflicting commands over real DCB Axon Server: one 204, one 422 probe-claimed, one event. */
@SpringBootTest
@ActiveProfiles("probes-race")
@AutoConfigureMockMvc
class RaceOverAxonServerTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun axonServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("axon.axonserver.servers") { SharedAxonServer.address }
        }
    }

    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var unitOfWorkFactory: UnitOfWorkFactory

    @Autowired lateinit var eventSource: StreamableEventSource

    @Autowired lateinit var tokenSource: TrackingTokenSource

    @Autowired lateinit var eventConverter: EventConverter

    private fun claimOnce(claim: UUID): Pair<Int, String> {
        val result = mockMvc.post("/probe-claim?claimId=$claim") { accept = MediaType.APPLICATION_PROBLEM_JSON }.andReturn()
        return result.response.status to result.response.contentAsString
    }

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
    fun `two concurrent claims on one id give one 204, one 422 probe-claimed, and one event`() {
        repeat(10) {
            val claim = UUID.randomUUID()
            val pool = Executors.newFixedThreadPool(2)
            try {
                val outcomes = pool.invokeAll(listOf(Callable { claimOnce(claim) }, Callable { claimOnce(claim) })).map { it.get() }
                val statuses = outcomes.map { it.first }.sorted()
                assertEquals(listOf(204, 422), statuses, "outcomes: $outcomes")
                val loser = outcomes.single { it.first == 422 }
                assertEquals(true, loser.second.contains("probe-claimed"), "loser body: ${loser.second}")
                assertEquals(1, storedClaims(claim), "stored claims for $claim")
            } finally {
                pool.shutdownNow()
            }
        }
    }
}
