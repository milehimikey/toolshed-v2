package com.toolshed.myloans

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import io.axoniq.framework.testcontainer.AxonServerContainer
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

/** My Loans over REST against a real Axon Server: the Spring-registered projection, the query, and the identity refusals. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MyLoansEndpointTest {
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

    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var eventGateway: EventGateway

    @Autowired lateinit var unitOfWorkFactory: UnitOfWorkFactory

    private val now = Instant.parse("2026-10-06T04:00:00Z")

    private fun publish(vararg events: Any) {
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events.toList()) }
        uow.execute().join()
    }

    private fun read(
        neighbor: String,
        caller: String? = neighbor,
    ) = mockMvc.get("/api/neighbors/$neighbor/loans") {
        accept = MediaType.APPLICATION_JSON
        caller?.let { header("X-Toolshed-Neighbor-Id", it) }
    }

    /** The view is eventually consistent: poll until [check] holds on the body, or give up. */
    private fun awaitBody(
        neighbor: NeighborId,
        check: (String) -> Boolean,
    ): String {
        val deadline = Instant.now().plus(Duration.ofSeconds(15))
        var body = ""
        while (Instant.now().isBefore(deadline)) {
            body = read(neighbor.value.toString()).andReturn().response.contentAsString
            if (check(body)) return body
            Thread.sleep(200)
        }
        return body
    }

    @Test
    fun `INV-MYL-14 - given no sign-in, when My Loans is asked for, then refused not-signed-in`() {
        read(UUID.randomUUID().toString(), caller = null).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-MYL-14") }
            jsonPath("$.reason") { value("not-signed-in") }
            jsonPath("$.detail") { value("Please sign in.") }
        }
    }

    @Test
    fun `INV-MYL-15 - given Ben signed in, when Ana's loans are asked for, then refused requester-mismatch`() {
        read(UUID.randomUUID().toString(), caller = UUID.randomUUID().toString()).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-MYL-15") }
            jsonPath("$.reason") { value("requester-mismatch") }
            jsonPath("$.detail") { value("You can only see your own loans.") }
        }
    }

    @Test
    fun `INV-MYL-1 - given no open loans, when Ana asks for her own loans, then the list is empty`() {
        val ana = NeighborId(UUID.randomUUID())
        read(ana.value.toString()).andExpect {
            status { isOk() }
            jsonPath("$.loans") { isEmpty() }
        }
    }

    @Test
    fun `INV-MYL-2 INV-MYL-7 - given a checked-out loan, when Ana reads her own loans, then it lists the tool and due date`() {
        val tool = ToolId(UUID.randomUUID())
        val ana = NeighborId(UUID.randomUUID())
        val loan = LoanId(UUID.randomUUID())
        val due = LocalDate.now().plusDays(7)
        publish(
            ToolAdded(tool, "Drill ${tool.value}", null, 7, now),
            ToolCheckedOut(loan, tool, ana, 7, due, now),
        )

        val body = awaitBody(ana) { it.contains(loan.value.toString()) }
        kotlin.test.assertTrue(body.contains("\"dueDate\":\"$due\""), body)
        kotlin.test.assertTrue(body.contains("\"overdue\":false"), body)
        kotlin.test.assertTrue(body.contains("\"canExtend\":true"), body)
    }
}
