package com.toolshed.recordtoollost

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.web.CallerArgumentResolver
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

/** Record Tool Lost through the running application: REST in front, Axon Server (DCB) behind. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RecordToolLostEndToEndTest {
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

    private val now = Instant.now()

    private fun record(vararg events: Any) {
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events.toList()) }
        uow.execute().join()
    }

    private fun lost(
        tool: String,
        loan: String,
        manager: Boolean = true,
    ) = mockMvc.put("/api/tools/$tool/loans/$loan/lost") {
        if (manager) {
            header(CallerArgumentResolver.ROLE_HEADER, CallerArgumentResolver.SHED_MANAGER_ROLE)
        } else {
            // Signed in as a neighbor, so the request is past sign-in and fails only the role check.
            header(CallerArgumentResolver.NEIGHBOR_HEADER, UUID.randomUUID().toString())
        }
    }

    @Test
    fun `INV-LOST-1 INV-LOST-2 - the shed manager records a loan lost and gets 204, and a repeat is also 204`() {
        val tool = ToolId(UUID.randomUUID())
        val ana = NeighborId(UUID.randomUUID())
        val loan = LoanId(UUID.randomUUID())
        record(
            ToolAdded(tool, "Ladder", null, 7, now),
            NeighborRegistered(ana, "Ana", "ana@example.com", now),
            ToolCheckedOut(loan, tool, ana, 7, LocalDate.now().plusDays(7), now),
        )
        lost(tool.value.toString(), loan.value.toString()).andExpect {
            status { isNoContent() }
            header { doesNotExist("Location") }
        }
        val lost = recorded(ToolLost::class.java) { it.loanId == loan }
        assertThat(lost).hasSize(1)
        assertThat(lost.single().toolId).isEqualTo(tool)
        // INV-LOST-3: recording a loss records only Tool Lost, so no Tool Retired is recorded for the tool.
        assertThat(recorded(ToolRetired::class.java) { it.toolId == tool }).isEmpty()
        lost(tool.value.toString(), loan.value.toString()).andExpect { status { isNoContent() } }
        // INV-LOST-10: the repeat records nothing new.
        assertThat(recorded(ToolLost::class.java) { it.loanId == loan }).hasSize(1)
    }

    /** Reads the store from the start for a few seconds and returns the [type] events that match [keep]. */
    private fun <T : Any> recorded(
        type: Class<T>,
        keep: (T) -> Boolean,
    ): List<T> {
        val found = mutableListOf<T>()
        val deadline = Instant.now().plus(Duration.ofSeconds(5))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry ->
                            val message: EventMessage = entry.message()
                            if (message.type().name() == type.name) {
                                eventConverter
                                    .convertPayload<EventMessage, T>(message, type)
                                    ?.takeIf(keep)
                                    ?.let { found.add(it) }
                            }
                        }
                    }
                    stream.close()
                }
            }.join()
        return found
    }

    @Test
    fun `INV-LOST-11 - a toolId that is not the loan's tool is refused loan-not-found`() {
        val tool = ToolId(UUID.randomUUID())
        val ana = NeighborId(UUID.randomUUID())
        val loan = LoanId(UUID.randomUUID())
        record(
            ToolAdded(tool, "Ladder", null, 7, now),
            NeighborRegistered(ana, "Ana", "ana@example.com", now),
            ToolCheckedOut(loan, tool, ana, 7, LocalDate.now().plusDays(7), now),
        )
        lost(UUID.randomUUID().toString(), loan.value.toString()).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.reason") { value("loan-not-found") }
            jsonPath("$.invariant") { value("INV-LOST-11") }
            jsonPath("$.detail") { value("There is no such loan.") }
        }
    }

    @Test
    fun `INV-LOST-1 - a caller without the shed-manager role is refused not-shed-manager`() {
        lost(UUID.randomUUID().toString(), UUID.randomUUID().toString(), manager = false).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.reason") { value("not-shed-manager") }
            jsonPath("$.invariant") { value("INV-LOST-1") }
            jsonPath("$.detail") { value("Only the shed manager can record tools lost.") }
        }
    }

    @Test
    fun `INV-LOST-12 - a request with no sign-in is refused not-signed-in`() {
        mockMvc.put("/api/tools/${UUID.randomUUID()}/loans/${UUID.randomUUID()}/lost").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.reason") { value("not-signed-in") }
            jsonPath("$.invariant") { value("INV-LOST-12") }
            jsonPath("$.detail") { value("Please sign in.") }
        }
    }
}
