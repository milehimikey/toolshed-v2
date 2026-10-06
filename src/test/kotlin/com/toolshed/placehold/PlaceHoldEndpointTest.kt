package com.toolshed.placehold

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
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
import org.springframework.test.web.servlet.put
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Place Hold over REST, through Spring and a real Axon Server. The shed is seeded through the event
 * gateway. Success is checked by what is recorded. Refusals must come back as 422 ProblemDetails
 * carrying the slice's `invariant` and `reason`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlaceHoldEndpointTest {
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

    /** A drill on loan to Ana, with Ben registered. Fresh ids per test, so each test reads only its own shed. */
    private data class Shed(
        val tool: ToolId,
        val ana: NeighborId,
        val ben: NeighborId,
    )

    private fun seedShed(): Shed {
        val shed = Shed(ToolId(UUID.randomUUID()), NeighborId(UUID.randomUUID()), NeighborId(UUID.randomUUID()))
        val events: List<Any> =
            listOf(
                ToolAdded(shed.tool, "Cordless drill", null, 7, now),
                NeighborRegistered(shed.ana, "Ana Ruiz", "ana-${shed.ana.value}@example.org", now),
                NeighborRegistered(shed.ben, "Ben Cole", "ben-${shed.ben.value}@example.org", now),
                ToolCheckedOut(LoanId(UUID.randomUUID()), shed.tool, shed.ana, 7, LocalDate.of(2026, 10, 12), now),
            )
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events) }
        uow.execute().join()
        return shed
    }

    private fun put(
        neighbor: NeighborId,
        hold: UUID,
        body: String,
        signedInAs: NeighborId? = neighbor,
    ) = mockMvc.put("/api/neighbors/${neighbor.value}/holds/$hold") {
        signedInAs?.let { header("X-Toolshed-Neighbor-Id", it.value.toString()) }
        contentType = MediaType.APPLICATION_JSON
        content = body
        accept = MediaType.APPLICATION_PROBLEM_JSON
    }

    @Test
    fun `INV-HOLD-5 INV-HOLD-13 PUT a first hold answers 201 and records one Hold Placed`() {
        val shed = seedShed()
        val hold = UUID.randomUUID()

        put(shed.ben, hold, """{"toolId":"${shed.tool.value}"}""").andExpect { status { isEqualTo(201) } }

        assertThat(placedFor(hold)).hasSize(1)
    }

    @Test
    fun `INV-HOLD-13 PUT the same hold again answers 201 and records nothing new`() {
        val shed = seedShed()
        val hold = UUID.randomUUID()
        val body = """{"toolId":"${shed.tool.value}"}"""

        put(shed.ben, hold, body).andExpect { status { isEqualTo(201) } }
        put(shed.ben, hold, body).andExpect { status { isEqualTo(201) } }

        assertThat(placedFor(hold)).hasSize(1)
    }

    @Test
    fun `INV-HOLD-1 PUT a hold for Ben while signed in as Ana answers 422 requester-mismatch`() {
        val shed = seedShed()

        put(shed.ben, UUID.randomUUID(), """{"toolId":"${shed.tool.value}"}""", signedInAs = shed.ana).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-HOLD-1") }
            jsonPath("$.reason") { value("requester-mismatch") }
            jsonPath("$.detail") { value("You can only place holds for yourself.") }
        }
    }

    @Test
    fun `INV-HOLD-14 PUT a hold on an unknown tool answers 422 tool-not-found`() {
        // Fails over Axon Server until the foundation fix maps a handler's CommandRejected to 422.
        val shed = seedShed()

        put(shed.ben, UUID.randomUUID(), """{"toolId":"${UUID.randomUUID()}"}""").andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-HOLD-14") }
            jsonPath("$.reason") { value("tool-not-found") }
            jsonPath("$.detail") { value("There is no such tool.") }
        }
    }

    /** Reads the store from the start for a few seconds and returns the Hold Placed events for [hold]. */
    private fun placedFor(hold: UUID): List<HoldPlaced> {
        val found = mutableListOf<HoldPlaced>()
        val deadline = Instant.now().plus(Duration.ofSeconds(5))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry ->
                            val message: EventMessage = entry.message()
                            if (message.type().name() == HoldPlaced::class.java.name) {
                                decode(message)?.takeIf { it.holdId.value == hold }?.let { found.add(it) }
                            }
                        }
                    }
                    stream.close()
                }
            }.join()
        return found
    }

    private fun decode(message: EventMessage): HoldPlaced? =
        eventConverter.convertPayload<EventMessage, HoldPlaced>(message, HoldPlaced::class.java)
}
