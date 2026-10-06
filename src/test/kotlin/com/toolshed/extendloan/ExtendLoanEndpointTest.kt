package com.toolshed.extendloan

import io.axoniq.framework.testcontainer.AxonServerContainer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * The HTTP surface of Extend Loan, run through Spring and a real Axon Server. Refusals must come
 * back as 422 ProblemDetails carrying the slice's `invariant` and `reason`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ExtendLoanEndpointTest {
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

    private val ana = UUID.nameUUIDFromBytes("endpoint-ana".toByteArray())
    private val ben = UUID.nameUUIDFromBytes("endpoint-ben".toByteArray())
    private val unknownLoan = UUID.nameUUIDFromBytes("endpoint-no-such-loan".toByteArray())

    @Test
    fun `INV-EXT-1 given a request naming Ana from Ben, when extending, then 422 requester-mismatch`() {
        mockMvc
            .post("/api/neighbors/$ana/loans/$unknownLoan/extensions") {
                header("X-Toolshed-Neighbor-Id", ben.toString())
                contentType = MediaType.APPLICATION_JSON
                content = """{"fromDueDate":"2026-10-12"}"""
                accept = MediaType.APPLICATION_PROBLEM_JSON
            }.andExpect {
                status { isEqualTo(422) }
                jsonPath("$.invariant") { value("INV-EXT-1") }
                jsonPath("$.reason") { value("requester-mismatch") }
                jsonPath("$.detail") { value("You can only extend loans for yourself.") }
            }
    }

    @Test
    fun `INV-EXT-1 given no sign-in, when extending, then 422 not-signed-in`() {
        mockMvc
            .post("/api/neighbors/$ana/loans/$unknownLoan/extensions") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"fromDueDate":"2026-10-12"}"""
                accept = MediaType.APPLICATION_PROBLEM_JSON
            }.andExpect {
                status { isEqualTo(422) }
                jsonPath("$.invariant") { value("INV-EXT-1") }
                jsonPath("$.reason") { value("not-signed-in") }
            }
    }

    @Test
    fun `INV-EXT-9 given no such loan, when Ana extends it, then 422 loan-not-found`() {
        // Fails over Axon Server until the foundation fix maps a handler's CommandRejected to 422.
        mockMvc
            .post("/api/neighbors/$ana/loans/$unknownLoan/extensions") {
                header("X-Toolshed-Neighbor-Id", ana.toString())
                contentType = MediaType.APPLICATION_JSON
                content = """{"fromDueDate":"2026-10-12"}"""
                accept = MediaType.APPLICATION_PROBLEM_JSON
            }.andExpect {
                status { isEqualTo(422) }
                jsonPath("$.invariant") { value("INV-EXT-9") }
                jsonPath("$.reason") { value("loan-not-found") }
                jsonPath("$.detail") { value("There is no such loan.") }
            }
    }
}
