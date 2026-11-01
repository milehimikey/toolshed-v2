package com.toolshed.toolinventory

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.registerneighbor.NeighborRegistered
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.gateway.EventGateway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The Tool Inventory over REST, through Spring and a real Axon Server: the shed manager's screen alone. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ToolInventoryEndpointTest {
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

    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val mapper = ObjectMapper()

    private fun publish(vararg events: Any) {
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events.toList()) }
        uow.execute().join()
    }

    private fun listAsManager() =
        mockMvc.get("/api/tools/inventory") {
            header("X-Toolshed-Role", "shed-manager")
            accept = MediaType.APPLICATION_JSON
        }

    private fun rowFor(tool: ToolId): JsonNode {
        var found: JsonNode? = null
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted {
            val body = listAsManager().andReturn().response.contentAsString
            found = mapper.readTree(body).path("tools").firstOrNull { it.path("toolId").asText() == tool.value.toString() }
            assertThat(found).isNotNull
        }
        return found!!
    }

    @Test
    fun `INV-TINV-4 INV-TINV-7 the shed manager lists an added tool with its fields, retire offered`() {
        val tool = ToolId(UUID.randomUUID())
        publish(ToolAdded(tool, "Inventory drill ${tool.value}", "18V", 7, now))
        val row = rowFor(tool)
        assertThat(row.path("description").asText()).isEqualTo("18V")
        assertThat(row.path("defaultCheckoutPeriodDays").asInt()).isEqualTo(7)
        assertThat(row.path("onLoan").asBoolean()).isFalse()
        assertThat(row.path("canRetire").asBoolean()).isTrue()
        assertThat(row.path("canMarkLost").asBoolean()).isFalse()
    }

    @Test
    fun `INV-TINV-5 INV-TINV-9 an out tool shows the borrower's name and due date, overdue, and never the email`() {
        val tool = ToolId(UUID.randomUUID())
        val neighbor = NeighborId(UUID.randomUUID())
        val due = LocalDate.now().minusDays(3)
        publish(
            ToolAdded(tool, "Inventory ladder ${tool.value}", null, 7, now),
            NeighborRegistered(neighbor, "Endpoint Ana", "ana-secret@example.org", now),
            ToolCheckedOut(LoanId(UUID.randomUUID()), tool, neighbor, 7, due, now),
        )
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted {
            assertThat(rowFor(tool).path("onLoan").asBoolean()).isTrue()
        }
        val row = rowFor(tool)
        assertThat(row.path("borrowerName").asText()).isEqualTo("Endpoint Ana")
        assertThat(row.path("dueDate").asText()).isEqualTo(due.toString())
        assertThat(row.path("overdue").asBoolean()).isTrue()
        assertThat(row.path("canMarkLost").asBoolean()).isTrue()
        assertThat(row.toString()).doesNotContain("ana-secret@example.org")
    }

    @Test
    fun `INV-TINV-12 a neighbor without the shed-manager role answers 422 not-shed-manager`() {
        mockMvc
            .get("/api/tools/inventory") {
                header("X-Toolshed-Neighbor-Id", UUID.randomUUID().toString())
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isEqualTo(422) }
                jsonPath("$.invariant") { value("INV-TINV-12") }
                jsonPath("$.reason") { value("not-shed-manager") }
                jsonPath("$.detail") { value("Only the shed manager can see the tool inventory.") }
            }
    }

    @Test
    fun `INV-TINV-11 a request with no sign-in answers 422 not-signed-in`() {
        mockMvc
            .get("/api/tools/inventory") { accept = MediaType.APPLICATION_JSON }
            .andExpect {
                status { isEqualTo(422) }
                jsonPath("$.invariant") { value("INV-TINV-11") }
                jsonPath("$.reason") { value("not-signed-in") }
                jsonPath("$.detail") { value("Please sign in.") }
            }
    }
}
