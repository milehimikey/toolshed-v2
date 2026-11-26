package com.toolshed.scheduletoolmaintenance

import io.axoniq.framework.testcontainer.AxonServerContainer
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
import java.util.UUID

/**
 * Schedule Tool Maintenance over REST, through Spring and a real Axon Server. These cover the identity and bad-input
 * checks that run before any event is read, so no tool needs seeding.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ScheduleToolMaintenanceEndpointTest {
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

    private fun schedule(
        tool: String = UUID.randomUUID().toString(),
        role: String? = "shed-manager",
        name: String? = "Dana",
        neighbor: Boolean = false,
        body: String = """{"tasks":["Oil hinges"],"scheduledBy":"Someone Else"}""",
    ) = mockMvc.put("/api/tools/$tool/maintenance") {
        if (neighbor) header("X-Toolshed-Neighbor-Id", UUID.randomUUID().toString())
        role?.let { header("X-Toolshed-Role", it) }
        name?.let { header("X-Toolshed-Manager-Name", it) }
        contentType = MediaType.APPLICATION_JSON
        content = body
        accept = MediaType.APPLICATION_PROBLEM_JSON
    }

    @Test
    fun `INV-STM-1 PUT maintenance with no sign-in answers 422 not-signed-in`() {
        schedule(role = null, name = null).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.reason") { value("not-signed-in") }
            jsonPath("$.detail") { value("Please sign in.") }
        }
    }

    @Test
    fun `INV-STM-1 PUT maintenance as a neighbor without the role answers 422 not-shed-manager`() {
        schedule(role = null, name = null, neighbor = true).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-STM-1") }
            jsonPath("$.reason") { value("not-shed-manager") }
            jsonPath("$.detail") { value("Only the shed manager can schedule tool maintenance.") }
        }
    }

    @Test
    fun `INV-STM-10 PUT maintenance with the role but no name answers 422 not-signed-in`() {
        schedule(name = null).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.reason") { value("not-signed-in") }
        }
        schedule(name = "   ").andExpect {
            status { isEqualTo(422) }
            jsonPath("$.reason") { value("not-signed-in") }
        }
    }

    @Test
    fun `INV-STM-1 a malformed tool id is bad input 400 for the shed manager, but not-signed-in still comes first`() {
        schedule(tool = "not-a-uuid").andExpect { status { isEqualTo(400) } }
        schedule(tool = "not-a-uuid", role = null, name = null).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.reason") { value("not-signed-in") }
        }
    }

    @Test
    fun `INV-STM-2 PUT maintenance with blank tasks answers 422 tasks-missing`() {
        schedule(body = """{"tasks":["  "]}""").andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-STM-2") }
            jsonPath("$.reason") { value("tasks-missing") }
        }
    }
}
