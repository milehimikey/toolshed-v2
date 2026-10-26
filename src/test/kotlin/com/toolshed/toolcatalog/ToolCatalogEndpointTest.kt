package com.toolshed.toolcatalog

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.retiretool.ToolRetired
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
 * The Tool Catalog view over REST, through Spring and a real Axon Server. Events are published through the
 * gateway, and the view is read until it has caught up (eventual consistency). Any signed-in neighbor may read it,
 * and every neighbor gets the same answer (INV-TCAT-5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ToolCatalogEndpointTest {
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

    private fun listAsNeighbor(neighborId: UUID) =
        mockMvc.get("/api/tools/catalog") {
            header("X-Toolshed-Neighbor-Id", neighborId.toString())
            accept = MediaType.APPLICATION_JSON
        }

    /** The view's row for [tool], once it appears in the list. */
    private fun rowFor(tool: ToolId): JsonNode {
        var found: JsonNode? = null
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted {
            val body = listAsNeighbor(UUID.randomUUID()).andReturn().response.contentAsString
            found = mapper.readTree(body).path("tools").firstOrNull { it.path("toolId").asText() == tool.value.toString() }
            assertThat(found).isNotNull
        }
        return found!!
    }

    @Test
    fun `INV-TCAT-1 a tool added is listed as available, with no holds waiting, offering check out and not place hold`() {
        val tool = ToolId(UUID.randomUUID())
        publish(ToolAdded(tool, "Drill ${tool.value}", "18V, two batteries", 7, now))

        val row = rowFor(tool)
        assertThat(row.path("description").asText()).isEqualTo("18V, two batteries")
        assertThat(row.path("status").asText()).isEqualTo(ToolCatalogStatus.AVAILABLE)
        assertThat(row.path("holdsWaiting").asInt()).isZero()
        assertThat(row.path("canCheckOut").asBoolean()).isTrue()
        assertThat(row.path("canPlaceHold").asBoolean()).isFalse()
    }

    @Test
    fun `INV-TCAT-2 INV-TCAT-5 a checked-out tool shows checked out and its due date, with no borrower in the answer`() {
        val tool = ToolId(UUID.randomUUID())
        val due = LocalDate.now().plusDays(7)
        publish(
            ToolAdded(tool, "Drill ${tool.value}", null, 7, now),
            ToolCheckedOut(LoanId(UUID.randomUUID()), tool, NeighborId(UUID.randomUUID()), 7, due, now),
        )

        val row = rowFor(tool)
        assertThat(row.path("status").asText()).isEqualTo(ToolCatalogStatus.CHECKED_OUT)
        assertThat(row.path("dueDate").asText()).isEqualTo(due.toString())
        assertThat(row.has("borrowerName")).isFalse()
        assertThat(row.has("borrowerId")).isFalse()
    }

    @Test
    fun `INV-TCAT-8 a tool retired is not listed`() {
        val tool = ToolId(UUID.randomUUID())
        publish(
            ToolAdded(tool, "Ladder ${tool.value}", null, 7, now),
            ToolRetired(tool, now),
        )
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted {
            val body = listAsNeighbor(UUID.randomUUID()).andReturn().response.contentAsString
            assertThat(mapper.readTree(body).path("tools").map { it.path("toolId").asText() })
                .doesNotContain(tool.value.toString())
        }
    }

    @Test
    fun `INV-TCAT-5 every neighbor sees the same catalog`() {
        val tool = ToolId(UUID.randomUUID())
        publish(ToolAdded(tool, "Drill ${tool.value}", null, 7, now))
        rowFor(tool) // wait until it has landed

        val ana = listAsNeighbor(UUID.randomUUID()).andReturn().response.contentAsString
        val ben = listAsNeighbor(UUID.randomUUID()).andReturn().response.contentAsString
        assertThat(mapper.readTree(ana)).isEqualTo(mapper.readTree(ben))
    }

    @Test
    fun `INV-TCAT-14 a request with no sign-in is refused not-signed-in`() {
        mockMvc.get("/api/tools/catalog") { accept = MediaType.APPLICATION_PROBLEM_JSON }.andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-TCAT-14") }
            jsonPath("$.reason") { value("not-signed-in") }
        }
    }
}
