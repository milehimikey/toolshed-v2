package com.toolshed.reinstateneighbor

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.placehold.HoldPlaced
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
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
import org.springframework.test.web.servlet.delete
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
class ReinstateNeighborEndpointTest {
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

    private fun reinstate(
        neighbor: NeighborId,
        role: String? = "shed-manager",
        neighborHeader: String? = null,
    ) = mockMvc.delete("/api/neighbors/${neighbor.value}/suspension") {
        accept = MediaType.APPLICATION_PROBLEM_JSON
        role?.let { header("X-Toolshed-Role", it) }
        neighborHeader?.let { header("X-Toolshed-Neighbor-Id", it) }
    }

    /** Stored Neighbor Reinstated events for [neighbor], read from the start of the store over a short window. */
    private fun reinstatedCount(neighbor: NeighborId): Int {
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
            .filter { it.type().name() == NeighborReinstated::class.java.name }
            .map { checkNotNull(eventConverter.convertPayload<EventMessage, NeighborReinstated>(it, NeighborReinstated::class.java)) }
            .count { it.neighborId == neighbor }
    }

    @Test
    fun `INV-REI-9 - given nobody is signed in, when reinstated, then refused not-signed-in`() {
        reinstate(NeighborId(UUID.randomUUID()), role = null).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-REI-9") }
            jsonPath("$.reason") { value("not-signed-in") }
            jsonPath("$.detail") { value("Please sign in.") }
        }
    }

    @Test
    fun `INV-REI-1 - given a neighbor without the role, when reinstated, then refused not-shed-manager`() {
        val n = NeighborId(UUID.randomUUID())
        reinstate(n, role = null, neighborHeader = UUID.randomUUID().toString()).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-REI-1") }
            jsonPath("$.reason") { value("not-shed-manager") }
            jsonPath("$.detail") { value("Only the shed manager can reinstate neighbors.") }
        }
    }

    @Test
    fun `INV-REI-2 - given Ana is suspended with late returns and a hold, when reinstated, then recorded and nothing else`() {
        val n1 = NeighborId(UUID.randomUUID())
        val drill = ToolId(UUID.randomUUID())
        val ladder = ToolId(UUID.randomUUID())
        given(
            NeighborRegistered(n1, "Ana", "ana@example.com", now),
            NeighborSuspended(n1, now),
            ToolCheckedOut(LoanId(UUID.randomUUID()), drill, n1, 7, today, now),
            ToolReturned(LoanId(UUID.randomUUID()), drill, now, today, true),
            ToolCheckedOut(LoanId(UUID.randomUUID()), ladder, n1, 3, today, now),
            ToolReturned(LoanId(UUID.randomUUID()), ladder, now, today, true),
            HoldPlaced(HoldId(UUID.randomUUID()), drill, n1, now),
        )
        reinstate(n1).andExpect { status { isEqualTo(204) } }
        assertEquals(1, reinstatedCount(n1))
    }

    @Test
    fun `INV-REI-7 - given N1 is already active, when reinstated again, then succeeds and records nothing new`() {
        val n1 = NeighborId(UUID.randomUUID())
        given(
            NeighborRegistered(n1, "Ana", "ana@example.com", now),
            NeighborSuspended(n1, now),
            NeighborReinstated(n1, now),
        )
        reinstate(n1).andExpect { status { isEqualTo(204) } }
        assertEquals(1, reinstatedCount(n1))
    }

    @Test
    fun `INV-REI-7 - given Ben has never been suspended, when reinstated, then succeeds and records nothing`() {
        val n2 = NeighborId(UUID.randomUUID())
        given(NeighborRegistered(n2, "Ben", "ben@example.com", now))
        reinstate(n2).andExpect { status { isEqualTo(204) } }
        assertEquals(0, reinstatedCount(n2))
    }

    @Test
    fun `INV-REI-2 - given a late copy after a new suspension, when it arrives, then reinstates again`() {
        val n1 = NeighborId(UUID.randomUUID())
        given(
            NeighborRegistered(n1, "Ana", "ana@example.com", now),
            NeighborSuspended(n1, now),
            NeighborReinstated(n1, now),
            NeighborSuspended(n1, now),
        )
        reinstate(n1).andExpect { status { isEqualTo(204) } }
        assertEquals(2, reinstatedCount(n1))
    }

    @Test
    fun `INV-REI-2 - given N3 was suspended and then closed, when reinstated, then refused account-closed`() {
        val n3 = NeighborId(UUID.randomUUID())
        given(
            NeighborRegistered(n3, "Cy", "cy@example.com", now),
            NeighborSuspended(n3, now),
            NeighborAccountClosed(n3, true, now),
        )
        reinstate(n3).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-REI-2") }
            jsonPath("$.reason") { value("account-closed") }
            jsonPath("$.detail") { value("This account is closed.") }
        }
        assertEquals(0, reinstatedCount(n3))
    }

    @Test
    fun `INV-REI-8 - given no account has N9, when reinstated, then refused neighbor-not-found`() {
        val n9 = NeighborId(UUID.randomUUID())
        reinstate(n9).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-REI-8") }
            jsonPath("$.reason") { value("neighbor-not-found") }
            jsonPath("$.detail") { value("There is no such neighbor.") }
        }
        assertEquals(0, reinstatedCount(n9))
    }

    @Test
    fun `INV-REI-8 - given a malformed neighbor id, when reinstated, then bad input 400 before any rule`() {
        mockMvc
            .delete("/api/neighbors/not-a-uuid/suspension") {
                header("X-Toolshed-Role", "shed-manager")
            }.andExpect { status { isEqualTo(400) } }
    }
}
