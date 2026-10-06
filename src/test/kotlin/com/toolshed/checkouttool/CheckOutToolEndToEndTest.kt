package com.toolshed.checkouttool

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.web.CallerArgumentResolver
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
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * Check Out Tool through the running application: REST in front, Axon Server (DCB) behind.
 * Tools and neighbors are recorded directly as events, because Add Tool and Register Neighbor
 * are other slices.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CheckOutToolEndToEndTest {
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

    private fun body(
        loanId: UUID,
        toolId: UUID,
        neighborId: UUID,
        period: String? = null,
    ): String {
        val periodField = period?.let { ""","loanPeriodDays":$it""" } ?: ""
        return """{"loanId":"$loanId","toolId":"$toolId","neighborId":"$neighborId"$periodField}"""
    }

    private fun neighbor(id: NeighborId) = id.value.toString()

    @Test
    fun `INV-CHK-6 - a signed-in neighbor checks out a tool and gets 201, and a repeat of the same loan is also 201`() {
        val tool = ToolId(UUID.randomUUID())
        val ana = NeighborId(UUID.randomUUID())
        record(ToolAdded(tool, "Cordless drill", null, 7, now), NeighborRegistered(ana, "Ana", "ana@example.com", now))
        val loan = UUID.randomUUID()
        val request = body(loan, tool.value, ana.value)

        mockMvc
            .post("/loans") {
                contentType = MediaType.APPLICATION_JSON
                content = request
                header(CallerArgumentResolver.NEIGHBOR_HEADER, neighbor(ana))
            }.andExpect {
                status { isCreated() }
                header { string("Location", "/loans/$loan") }
            }

        mockMvc
            .post("/loans") {
                contentType = MediaType.APPLICATION_JSON
                content = request
                header(CallerArgumentResolver.NEIGHBOR_HEADER, neighbor(ana))
            }.andExpect {
                status { isCreated() }
            }
    }

    @Test
    fun `INV-CHK-2 - a second neighbor is refused tool-checked-out with the doc's sentence`() {
        val tool = ToolId(UUID.randomUUID())
        val ana = NeighborId(UUID.randomUUID())
        val ben = NeighborId(UUID.randomUUID())
        record(
            ToolAdded(tool, "Ladder", null, 7, now),
            NeighborRegistered(ana, "Ana", "ana@example.com", now),
            NeighborRegistered(ben, "Ben", "ben@example.com", now),
        )
        mockMvc
            .post("/loans") {
                contentType = MediaType.APPLICATION_JSON
                content = body(UUID.randomUUID(), tool.value, ana.value)
                header(CallerArgumentResolver.NEIGHBOR_HEADER, neighbor(ana))
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/loans") {
                contentType = MediaType.APPLICATION_JSON
                content = body(UUID.randomUUID(), tool.value, ben.value)
                header(CallerArgumentResolver.NEIGHBOR_HEADER, neighbor(ben))
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.reason") { value("tool-checked-out") }
                jsonPath("$.invariant") { value("INV-CHK-2") }
                jsonPath("$.detail") { value("This tool is already checked out.") }
            }
    }

    @Test
    fun `INV-CHK-1 - a request with no sign-in is refused not-signed-in`() {
        mockMvc
            .post("/loans") {
                contentType = MediaType.APPLICATION_JSON
                content = body(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.reason") { value("not-signed-in") }
                jsonPath("$.invariant") { value("INV-CHK-1") }
                jsonPath("$.detail") { value("Please sign in.") }
            }
    }

    @Test
    fun `INV-CHK-1 - a neighbor checking out for someone else is refused requester-mismatch`() {
        val ana = NeighborId(UUID.randomUUID())
        val ben = NeighborId(UUID.randomUUID())
        mockMvc
            .post("/loans") {
                contentType = MediaType.APPLICATION_JSON
                content = body(UUID.randomUUID(), UUID.randomUUID(), ana.value)
                header(CallerArgumentResolver.NEIGHBOR_HEADER, neighbor(ben))
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.reason") { value("requester-mismatch") }
                jsonPath("$.detail") { value("You can only check out tools for yourself.") }
            }
    }

    @Test
    fun `INV-CHK-6 - a fractional loan period is refused period-out-of-range, never rounded`() {
        val tool = ToolId(UUID.randomUUID())
        val ana = NeighborId(UUID.randomUUID())
        record(ToolAdded(tool, "Saw", null, 7, now), NeighborRegistered(ana, "Ana", "ana@example.com", now))
        mockMvc
            .post("/loans") {
                contentType = MediaType.APPLICATION_JSON
                content = body(UUID.randomUUID(), tool.value, ana.value, "2.5")
                header(CallerArgumentResolver.NEIGHBOR_HEADER, neighbor(ana))
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.reason") { value("period-out-of-range") }
            }
    }

    @Test
    fun `a non-numeric loan period is malformed input, 400, not a refusal`() {
        val ana = NeighborId(UUID.randomUUID())
        mockMvc
            .post("/loans") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"loanId":"${UUID.randomUUID()}","toolId":"${UUID.randomUUID()}","neighborId":"${ana.value}","loanPeriodDays":"abc"}"""
                header(CallerArgumentResolver.NEIGHBOR_HEADER, neighbor(ana))
            }.andExpect {
                status { isBadRequest() }
            }
    }
}
