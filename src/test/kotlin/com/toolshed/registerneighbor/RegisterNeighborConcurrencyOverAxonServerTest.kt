package com.toolshed.registerneighbor

import io.axoniq.framework.testcontainer.AxonServerContainer
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.put
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * INV-REG-4 under contention, over a real DCB Axon Server: sign-ups for one email fired at the same moment record
 * at most one Neighbor Registered. The enforcement is the append condition that NeighborRegistryState's criteria
 * put on `decideAndAppend`: a loser's append conflicts with the winner's, the dispatcher retries it, and the
 * re-decision sees the email held and refuses `email-in-use`. The slice doc's error flows promise exactly this.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RegisterNeighborConcurrencyOverAxonServerTest {
    companion object {
        @Container
        @JvmStatic
        val axonServer = AxonServerContainer().withDcbContext(true)

        @DynamicPropertySource
        @JvmStatic
        fun axonServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("axon.axonserver.servers") { axonServer.axonServerAddress }
        }

        private const val CONTENDERS = 8
        private const val ROUNDS = 3
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var unitOfWorkFactory: UnitOfWorkFactory

    @Autowired
    lateinit var eventSource: StreamableEventSource

    @Autowired
    lateinit var tokenSource: TrackingTokenSource

    @Autowired
    lateinit var eventConverter: EventConverter

    /** One sign-up's result: who asked, what came back. */
    private data class Outcome(
        val neighborId: UUID,
        val status: Int,
        val body: String,
    )

    @Test
    fun `INV-REG-4 eight sign-ups with one email at the same moment record one Neighbor Registered and refuse the rest email-in-use`() {
        repeat(ROUNDS) { round ->
            val local = "race-$round-${UUID.randomUUID()}"
            val pool = Executors.newFixedThreadPool(CONTENDERS)
            try {
                val go = CyclicBarrier(CONTENDERS)
                val contenders =
                    (0 until CONTENDERS).map { i ->
                        val id = UUID.randomUUID()
                        // INV-REG-4 compares ignoring case, so the contenders spell the address differently.
                        val email = spell("$local@example.org", i)
                        Callable {
                            go.await(10, TimeUnit.SECONDS)
                            val result =
                                mockMvc
                                    .put("/api/neighbors/$id") {
                                        contentType = MediaType.APPLICATION_JSON
                                        content = """{"name":"Neighbor $i","email":"$email"}"""
                                        accept = MediaType.APPLICATION_PROBLEM_JSON
                                    }.andReturn()
                            Outcome(id, result.response.status, result.response.contentAsString)
                        }
                    }
                val outcomes = pool.invokeAll(contenders, 90, TimeUnit.SECONDS).map { it.get() }

                assertThat(outcomes).describedAs("round $round outcomes").hasSize(CONTENDERS)
                assertThat(outcomes.filter { it.status >= 500 }).describedAs("round $round 5xx").isEmpty()

                val winners = outcomes.filter { it.status == 201 }
                assertThat(winners).describedAs("round $round winners: $outcomes").hasSize(1)

                val losers = outcomes - winners.toSet()
                assertThat(losers).describedAs("round $round losers").hasSize(CONTENDERS - 1)
                losers.forEach { loser ->
                    assertThat(loser.status).describedAs("loser ${loser.neighborId}: ${loser.body}").isEqualTo(422)
                    assertThat(loser.body).describedAs("loser ${loser.neighborId}").contains("\"invariant\":\"INV-REG-4\"")
                    assertThat(loser.body).describedAs("loser ${loser.neighborId}").contains("\"reason\":\"email-in-use\"")
                }

                val recorded = registeredWithEmail("$local@example.org")
                assertThat(recorded).describedAs("round $round Neighbor Registered for $local").hasSize(1)
                assertThat(recorded.single().neighborId.value).isEqualTo(winners.single().neighborId)
            } finally {
                pool.shutdownNow()
            }
        }
    }

    /** Spells one address three ways, so INV-REG-4's case-insensitive comparison is what the race exercises. */
    private fun spell(
        email: String,
        i: Int,
    ): String =
        when (i % 3) {
            0 -> email.lowercase()
            1 -> email.uppercase()
            else -> email.replaceFirstChar { it.uppercase() }.replace("@example.org", "@Example.Org")
        }

    /** Reads the store from the start for a few seconds and returns the Neighbor Registered events whose email matches [email] ignoring case. */
    private fun registeredWithEmail(email: String): List<NeighborRegistered> {
        val found = mutableListOf<NeighborRegistered>()
        val deadline = Instant.now().plus(Duration.ofSeconds(5))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry ->
                            val message: EventMessage = entry.message()
                            if (message.type().name() == NeighborRegistered::class.java.name) {
                                decode(message)
                                    ?.takeIf { it.email.equals(email, ignoreCase = true) }
                                    ?.let { found.add(it) }
                            }
                        }
                    }
                    stream.close()
                }
            }.join()
        return found
    }

    private fun decode(message: EventMessage): NeighborRegistered? =
        eventConverter.convertPayload<EventMessage, NeighborRegistered>(message, NeighborRegistered::class.java)
}
