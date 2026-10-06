package com.toolshed.closeaccount

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.registerneighbor.NeighborRegistered
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.EventMessage
import org.axonframework.messaging.eventhandling.conversion.EventConverter
import org.axonframework.messaging.eventhandling.gateway.EventGateway
import org.axonframework.messaging.eventstreaming.StreamableEventSource
import org.axonframework.messaging.eventstreaming.StreamingCondition
import org.axonframework.messaging.eventstreaming.TrackingTokenSource
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

/** The endpoint and the real Axon path: guards first, then the decider, against a real Axon Server. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CloseAccountEndpointTest {
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

    @Autowired lateinit var eventSource: StreamableEventSource

    @Autowired lateinit var tokenSource: TrackingTokenSource

    @Autowired lateinit var eventConverter: EventConverter

    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val today = LocalDate.of(2026, 10, 6)

    /** Publishes the Given events for one scenario, in order. */
    private fun given(vararg events: Any) {
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events.toList()) }
        uow.execute().join()
    }

    private fun close(
        neighbor: NeighborId,
        caller: String? = neighbor.value.toString(),
        role: String? = null,
    ) = mockMvc.put("/api/neighbors/${neighbor.value}/closure") {
        accept = MediaType.APPLICATION_PROBLEM_JSON
        caller?.let { header("X-Toolshed-Neighbor-Id", it) }
        role?.let { header("X-Toolshed-Role", it) }
    }

    /** Stored Neighbor Account Closed events for [neighbor], read from the start of the store over a short window. */
    private fun closedCount(neighbor: NeighborId): Int {
        val seen = mutableListOf<EventMessage>()
        val deadline = Instant.now().plus(Duration.ofSeconds(3))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { seen.add(it.message()) }
                    }
                    stream.close()
                }
            }.join()
        return seen
            .filter { it.type().name() == NeighborAccountClosed::class.java.name }
            .map { checkNotNull(eventConverter.convertPayload<EventMessage, NeighborAccountClosed>(it, NeighborAccountClosed::class.java)) }
            .count { it.neighborId == neighbor }
    }

    @Test
    fun `INV-CLS-1 - given no sign-in, when closing, then refused not-signed-in`() {
        close(NeighborId(UUID.randomUUID()), caller = null).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-CLS-1") }
            jsonPath("$.reason") { value("not-signed-in") }
            jsonPath("$.detail") { value("Please sign in.") }
        }
    }

    @Test
    fun `INV-CLS-1 - given someone else's account, when closing, then refused requester-mismatch`() {
        val ana = NeighborId(UUID.randomUUID())
        close(ana, caller = UUID.randomUUID().toString()).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-CLS-1") }
            jsonPath("$.reason") { value("requester-mismatch") }
            jsonPath("$.detail") { value("You can only close your own account.") }
        }
    }

    @Test
    fun `INV-CLS-9 - given Ana is registered and active, when she closes, then recorded and 204 over the Spring-registered module`() {
        val ana = NeighborId(UUID.randomUUID())
        given(NeighborRegistered(ana, "Ana", "ana@example.com", now))
        close(ana).andExpect { status { isEqualTo(204) } }
        assertEquals(1, closedCount(ana))
    }

    @Test
    fun `INV-CLS-2 - given Ana already closed, when she closes again, then 204 and nothing new recorded`() {
        val ana = NeighborId(UUID.randomUUID())
        given(
            NeighborRegistered(ana, "Ana", "ana@example.com", now),
            NeighborAccountClosed(ana, false, now),
        )
        close(ana).andExpect { status { isEqualTo(204) } }
        assertEquals(1, closedCount(ana))
    }

    @Test
    fun `INV-CLS-7 - given Ana has a tool on loan, when she closes, then refused tools-on-loan`() {
        val ana = NeighborId(UUID.randomUUID())
        given(
            NeighborRegistered(ana, "Ana", "ana@example.com", now),
            ToolCheckedOut(LoanId(UUID.randomUUID()), ToolId(UUID.randomUUID()), ana, 7, today.plusDays(7), now),
        )
        close(ana).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-CLS-7") }
            jsonPath("$.reason") { value("tools-on-loan") }
            jsonPath("$.detail") { value("Return all your tools before closing your account.") }
        }
        assertEquals(0, closedCount(ana))
    }

    @Test
    fun `INV-CLS-8 - given no account N9, when closed by N9, then refused neighbor-not-found`() {
        val n9 = NeighborId(UUID.randomUUID())
        close(n9).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-CLS-8") }
            jsonPath("$.reason") { value("neighbor-not-found") }
            jsonPath("$.detail") { value("There is no such neighbor.") }
        }
    }

    @Test
    fun `INV-CLS-8 - given a malformed neighbor id, when closing, then bad input 400 before any rule`() {
        mockMvc
            .put("/api/neighbors/not-a-uuid/closure") {
                header("X-Toolshed-Neighbor-Id", "not-a-uuid")
            }.andExpect { status { isEqualTo(400) } }
    }
}
