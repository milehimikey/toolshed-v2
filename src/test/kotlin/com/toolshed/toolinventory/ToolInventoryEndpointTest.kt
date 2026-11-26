package com.toolshed.toolinventory

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import io.axoniq.framework.testcontainer.AxonServerContainer
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

    private fun get(
        manager: Boolean,
        neighbor: NeighborId? = null,
    ) = mockMvc.get("/api/tools/inventory") {
        if (manager) header("X-Toolshed-Role", "shed-manager")
        neighbor?.let { header("X-Toolshed-Neighbor-Id", it.value.toString()) }
        accept = MediaType.APPLICATION_JSON
    }

    @Test
    fun `INV-TINV-1 GET as the shed manager answers 200 with the added tool`() {
        val tool = ToolId(UUID.randomUUID())
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx ->
            eventGateway.publish(ctx, listOf(ToolAdded(tool, "Endpoint drill", "18V", 7, Instant.parse("2026-10-06T04:00:00Z"))))
        }
        uow.execute().join()

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted {
            get(manager = true).andExpect {
                status { isEqualTo(200) }
                jsonPath("$[?(@.toolId=='${tool.value}')].name") { value("Endpoint drill") }
                jsonPath("$[?(@.toolId=='${tool.value}')].canRetire") { value(true) }
                jsonPath("$[?(@.toolId=='${tool.value}')].canMarkLost") { value(false) }
            }
        }
    }

    @Test
    fun `INV-TINV-12 GET as a neighbor answers 422 not-shed-manager`() {
        get(manager = false, neighbor = NeighborId(UUID.randomUUID())).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-TINV-12") }
            jsonPath("$.reason") { value("not-shed-manager") }
            jsonPath("$.detail") { value("Only the shed manager can see the tool inventory.") }
        }
    }

    @Test
    fun `INV-TINV-11 GET with no sign-in answers 422 not-signed-in`() {
        get(manager = false).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-TINV-11") }
            jsonPath("$.reason") { value("not-signed-in") }
            jsonPath("$.detail") { value("Please sign in.") }
        }
    }
}
