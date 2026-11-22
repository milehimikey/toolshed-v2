package com.toolshed.toolmaintenance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.toolshed.axon.ToolId
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceScheduled
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
import java.util.UUID

/** The Tool Maintenance view over REST, through Spring and a real Axon Server: the shed manager's screen alone. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ToolMaintenanceEndpointTest {
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

    private val mapper = ObjectMapper()

    private fun publish(vararg events: Any) {
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events.toList()) }
        uow.execute().join()
    }

    private fun listAsManager() =
        mockMvc.get("/api/tools/maintenance") {
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
    fun `INV-TMNT-1 the shed manager lists a tool in maintenance with its tasks and who scheduled it`() {
        val tool = ToolId(UUID.randomUUID())
        publish(
            ToolMaintenanceScheduled(
                tool,
                "Endpoint trimmer ${tool.value}",
                listOf("sharpen blades", "oil hinges"),
                null,
                "Sam",
                Instant.now(),
            ),
        )
        val row = rowFor(tool)
        assertThat(row.path("scheduledBy").asText()).isEqualTo("Sam")
        assertThat(row.path("tasks").map { it.asText() }).containsExactly("sharpen blades", "oil hinges")
    }

    @Test
    fun `INV-TMNT-10 a neighbor without the shed-manager role answers 422 not-shed-manager`() {
        mockMvc
            .get("/api/tools/maintenance") {
                header("X-Toolshed-Neighbor-Id", UUID.randomUUID().toString())
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isEqualTo(422) }
                jsonPath("$.invariant") { value("INV-TMNT-10") }
                jsonPath("$.reason") { value("not-shed-manager") }
                jsonPath("$.detail") { value("Only the shed manager can see the tools in maintenance.") }
            }
    }

    @Test
    fun `INV-TMNT-9 a request with no sign-in answers 422 not-signed-in`() {
        mockMvc
            .get("/api/tools/maintenance") { accept = MediaType.APPLICATION_JSON }
            .andExpect {
                status { isEqualTo(422) }
                jsonPath("$.invariant") { value("INV-TMNT-9") }
                jsonPath("$.reason") { value("not-signed-in") }
                jsonPath("$.detail") { value("Please sign in.") }
            }
    }
}
