package com.toolshed.toolinventory

import com.fasterxml.jackson.databind.ObjectMapper
import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.placehold.HoldPlaced
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

/**
 * The Tool Inventory view over REST, through Spring and a real Axon Server. Events are published through the
 * gateway, and the view is read until it has caught up (eventual consistency). Refusals return 422 with invariant
 * and reason.
 */
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

    private fun listAsShedManager() =
        mockMvc.get("/api/tools") {
            header("X-Toolshed-Role", "shed-manager")
            accept = MediaType.APPLICATION_JSON
        }

    /** The view's row for [tool], once it appears in the list. */
    private fun rowFor(tool: ToolId): com.fasterxml.jackson.databind.JsonNode {
        var found: com.fasterxml.jackson.databind.JsonNode? = null
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted {
            val body = listAsShedManager().andReturn().response.contentAsString
            found = mapper.readTree(body).path("tools").firstOrNull { it.path("toolId").asText() == tool.value.toString() }
            assertThat(found).isNotNull
        }
        return found!!
    }

    @Test
    fun `INV-TINV-1 INV-TINV-4 INV-TINV-5 a checked-out tool shows its name, description, borrower and due date`() {
        val tool = ToolId(UUID.randomUUID())
        val ana = NeighborId(UUID.randomUUID())
        val due = LocalDate.now().plusDays(7)
        publish(
            ToolAdded(tool, "Drill ${tool.value}", "18V, two batteries", 7, now),
            NeighborRegistered(ana, "Ana Ruiz", "ana-${ana.value}@example.org", now),
            ToolCheckedOut(LoanId(UUID.randomUUID()), tool, ana, 7, due, now),
        )

        val row = rowFor(tool)
        assertThat(row.path("description").asText()).isEqualTo("18V, two batteries")
        assertThat(row.path("defaultCheckoutPeriodDays").asInt()).isEqualTo(7)
        assertThat(row.path("onLoan").asBoolean()).isTrue()
        assertThat(row.path("borrowerName").asText()).isEqualTo("Ana Ruiz")
        assertThat(row.path("dueDate").asText()).isEqualTo(due.toString())
        assertThat(row.path("canMarkLost").asBoolean()).isTrue()
        assertThat(row.path("canRetire").asBoolean()).isTrue()
    }

    @Test
    fun `INV-TINV-3 a hold placed on a listed tool leaves its entry unchanged`() {
        val tool = ToolId(UUID.randomUUID())
        val ana = NeighborId(UUID.randomUUID())
        val ben = NeighborId(UUID.randomUUID())
        publish(
            ToolAdded(tool, "Ladder ${tool.value}", null, 7, now),
            NeighborRegistered(ana, "Ana Ruiz", "ana-${ana.value}@example.org", now),
            ToolCheckedOut(LoanId(UUID.randomUUID()), tool, ana, 7, LocalDate.now().plusDays(7), now),
        )
        val before = rowFor(tool)

        publish(HoldPlaced(HoldId(UUID.randomUUID()), tool, ben, now))

        assertThat(rowFor(tool)).isEqualTo(before)
    }

    @Test
    fun `INV-TINV-2 a tool retired in the shed is not listed`() {
        val tool = ToolId(UUID.randomUUID())
        publish(
            ToolAdded(tool, "Ladder ${tool.value}", null, 7, now),
            com.toolshed.retiretool.ToolRetired(tool, now),
        )
        // Wait for the view to see the add, then check the retired tool is absent.
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted {
            val body = listAsShedManager().andReturn().response.contentAsString
            assertThat(mapper.readTree(body).path("tools").map { it.path("toolId").asText() })
                .doesNotContain(tool.value.toString())
        }
    }

    @Test
    fun `INV-TINV-11 a request with no sign-in is refused not-signed-in`() {
        mockMvc.get("/api/tools") { accept = MediaType.APPLICATION_PROBLEM_JSON }.andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-TINV-11") }
            jsonPath("$.reason") { value("not-signed-in") }
        }
    }

    @Test
    fun `INV-TINV-12 a neighbor asking for the inventory is refused not-shed-manager`() {
        mockMvc
            .get("/api/tools") {
                header("X-Toolshed-Neighbor-Id", UUID.randomUUID().toString())
                accept = MediaType.APPLICATION_PROBLEM_JSON
            }.andExpect {
                status { isEqualTo(422) }
                jsonPath("$.invariant") { value("INV-TINV-12") }
                jsonPath("$.reason") { value("not-shed-manager") }
                jsonPath("$.detail") { value("Only the shed manager can see the tool inventory.") }
            }
    }
}
