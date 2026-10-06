package com.toolshed.recordtoollost

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.web.CallerArgumentResolver
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.gateway.EventGateway
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
    fun `INV-LOST-1 INV-LOST-2 - the shed manager records a loan lost and gets 201, and a repeat is also 201`() {
        val tool = ToolId(UUID.randomUUID())
        val ana = NeighborId(UUID.randomUUID())
        val loan = LoanId(UUID.randomUUID())
        record(
            ToolAdded(tool, "Ladder", null, 7, now),
            NeighborRegistered(ana, "Ana", "ana@example.com", now),
            ToolCheckedOut(loan, tool, ana, 7, LocalDate.now().plusDays(7), now),
        )
        lost(tool.value.toString(), loan.value.toString()).andExpect {
            status { isCreated() }
            header { string("Location", "/api/tools/${tool.value}/loans/${loan.value}/lost") }
        }
        lost(tool.value.toString(), loan.value.toString()).andExpect { status { isCreated() } }
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
