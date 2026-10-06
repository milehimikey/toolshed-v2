package com.toolshed.cancelhold

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.placehold.HoldPlaced
import com.toolshed.registerneighbor.NeighborRegistered
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.EventMessage
import org.axonframework.messaging.eventhandling.conversion.EventConverter
import org.axonframework.messaging.eventhandling.gateway.EventGateway
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
import org.springframework.test.web.servlet.delete
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Cancel Hold over REST, through Spring and a real Axon Server. The shed is seeded through the event
 * gateway. Success is checked by what is recorded. Refusals must come back as 422 ProblemDetails
 * carrying the slice's `invariant` and `reason`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CancelHoldEndpointTest {
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

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var eventGateway: EventGateway

    @Autowired
    lateinit var unitOfWorkFactory: UnitOfWorkFactory

    @Autowired
    lateinit var eventSource: StreamableEventSource

    @Autowired
    lateinit var tokenSource: TrackingTokenSource

    @Autowired
    lateinit var eventConverter: EventConverter

    private val now = Instant.parse("2026-10-06T04:00:00Z")

    /** A drill with Ben's hold waiting on it. Fresh ids per test, so each test reads only its own hold. */
    private data class Held(
        val hold: HoldId,
        val holder: NeighborId,
    )

    private fun seedHold(): Held {
        val tool = ToolId(UUID.randomUUID())
        val held = Held(HoldId(UUID.randomUUID()), NeighborId(UUID.randomUUID()))
        val events: List<Any> =
            listOf(
                ToolAdded(tool, "Cordless drill", null, 7, now),
                NeighborRegistered(held.holder, "Ben Cole", "ben-${held.holder.value}@example.org", now),
                HoldPlaced(held.hold, tool, held.holder, now),
            )
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events) }
        uow.execute().join()
        return held
    }

    private fun cancel(
        neighbor: UUID,
        hold: UUID,
        signedInAs: UUID = neighbor,
    ) = mockMvc.delete("/api/neighbors/$neighbor/holds/$hold") {
        header("X-Toolshed-Neighbor-Id", signedInAs.toString())
        accept = MediaType.APPLICATION_PROBLEM_JSON
    }

    @Test
    fun `INV-CNH-2 DELETE a waiting hold answers 204 and records one Hold Cancelled`() {
        val held = seedHold()

        cancel(held.holder.value, held.hold.value).andExpect { status { isNoContent() } }

        assertThat(cancelledFor(held.hold)).hasSize(1)
    }

    @Test
    fun `INV-CNH-8 DELETE the same hold again answers 204 and records nothing new`() {
        val held = seedHold()

        cancel(held.holder.value, held.hold.value).andExpect { status { isNoContent() } }
        cancel(held.holder.value, held.hold.value).andExpect { status { isNoContent() } }

        assertThat(cancelledFor(held.hold)).hasSize(1)
    }

    @Test
    fun `INV-CNH-1 DELETE a hold for Ben while signed in as Ana answers 422 requester-mismatch`() {
        val held = seedHold()
        val ana = UUID.randomUUID()

        cancel(held.holder.value, held.hold.value, signedInAs = ana).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-CNH-1") }
            jsonPath("$.reason") { value("requester-mismatch") }
            jsonPath("$.detail") { value("You can only cancel holds for yourself.") }
        }
    }

    @Test
    fun `INV-CNH-7 DELETE an unknown hold answers 422 hold-not-found`() {
        // Fails over Axon Server until the foundation fix maps a handler's CommandRejected to 422.
        val held = seedHold()

        cancel(held.holder.value, UUID.randomUUID()).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-CNH-7") }
            jsonPath("$.reason") { value("hold-not-found") }
            jsonPath("$.detail") { value("There is no such hold.") }
        }
    }

    /** Reads the store from the start for a few seconds and returns the Hold Cancelled events for [hold]. */
    private fun cancelledFor(hold: HoldId): List<HoldCancelled> {
        val found = mutableListOf<HoldCancelled>()
        val deadline = Instant.now().plus(Duration.ofSeconds(5))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry ->
                            val message: EventMessage = entry.message()
                            if (message.type().name() == HoldCancelled::class.java.name) {
                                decode(message)?.takeIf { it.holdId == hold }?.let { found.add(it) }
                            }
                        }
                    }
                    stream.close()
                }
            }.join()
        return found
    }

    private fun decode(message: EventMessage): HoldCancelled? =
        eventConverter.convertPayload<EventMessage, HoldCancelled>(message, HoldCancelled::class.java)
}
