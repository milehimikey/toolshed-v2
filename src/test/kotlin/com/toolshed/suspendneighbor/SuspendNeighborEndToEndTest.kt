package com.toolshed.suspendneighbor

import com.toolshed.axon.NeighborId
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.web.CallerArgumentResolver
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.assertj.core.api.Assertions.assertThat
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
import java.util.UUID

/**
 * Suspend Neighbor through the running application: REST in front, Axon Server (DCB) behind.
 * Neighbors are recorded as events, because Register Neighbor and Close Account are other slices.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SuspendNeighborEndToEndTest {
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

    @Autowired
    lateinit var eventSource: StreamableEventSource

    @Autowired
    lateinit var tokenSource: TrackingTokenSource

    @Autowired
    lateinit var eventConverter: EventConverter

    private val now = Instant.now()

    private fun record(vararg events: Any) {
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events.toList()) }
        uow.execute().join()
    }

    private fun suspend(
        neighbor: String,
        caller: (org.springframework.test.web.servlet.MockHttpServletRequestDsl) -> Unit = {},
    ) = mockMvc.put("/api/neighbors/$neighbor/suspension") {
        contentType = MediaType.APPLICATION_JSON
        caller(this)
    }

    private fun manager(request: org.springframework.test.web.servlet.MockHttpServletRequestDsl) {
        request.header(CallerArgumentResolver.ROLE_HEADER, CallerArgumentResolver.SHED_MANAGER_ROLE)
    }

    @Test
    fun `INV-SUS-1 - the shed manager suspends an active neighbor and gets 201`() {
        val ana = NeighborId(UUID.randomUUID())
        record(NeighborRegistered(ana, "Ana", "ana@example.com", now))
        suspend(ana.value.toString()) { manager(it) }.andExpect {
            status { isCreated() }
            header { string("Location", "/api/neighbors/${ana.value}/suspension") }
        }
        assertThat(suspendedFor(ana)).hasSize(1)
    }

    @Test
    fun `INV-SUS-9 - suspending an already suspended neighbor again is 201 and records nothing new`() {
        val ana = NeighborId(UUID.randomUUID())
        record(NeighborRegistered(ana, "Ana", "ana@example.com", now))
        suspend(ana.value.toString()) { manager(it) }.andExpect { status { isCreated() } }
        assertThat(suspendedFor(ana)).hasSize(1)
        suspend(ana.value.toString()) { manager(it) }.andExpect { status { isCreated() } }
        assertThat(suspendedFor(ana)).hasSize(1)
    }

    @Test
    fun `INV-SUS-2 - a closed account is refused account-closed`() {
        val ana = NeighborId(UUID.randomUUID())
        record(NeighborRegistered(ana, "Ana", "ana@example.com", now), NeighborAccountClosed(ana, false, now))
        suspend(ana.value.toString()) { manager(it) }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.reason") { value("account-closed") }
            jsonPath("$.invariant") { value("INV-SUS-2") }
            jsonPath("$.detail") { value("This account is closed.") }
        }
    }

    @Test
    fun `INV-SUS-1 - a caller without the shed-manager role is refused not-shed-manager`() {
        val ana = NeighborId(UUID.randomUUID())
        record(NeighborRegistered(ana, "Ana", "ana@example.com", now))
        suspend(ana.value.toString()) {
            it.header(CallerArgumentResolver.NEIGHBOR_HEADER, ana.value.toString())
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.reason") { value("not-shed-manager") }
            jsonPath("$.invariant") { value("INV-SUS-1") }
            jsonPath("$.detail") { value("Only the shed manager can suspend neighbors.") }
        }
    }

    @Test
    fun `INV-SUS-11 - a request with no sign-in is refused not-signed-in`() {
        suspend(UUID.randomUUID().toString()).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.reason") { value("not-signed-in") }
            jsonPath("$.invariant") { value("INV-SUS-11") }
            jsonPath("$.detail") { value("Please sign in.") }
        }
    }

    @Test
    fun `INV-SUS-10 - an unknown neighbor is refused neighbor-not-found`() {
        suspend(UUID.randomUUID().toString()) { manager(it) }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.reason") { value("neighbor-not-found") }
            jsonPath("$.invariant") { value("INV-SUS-10") }
        }
    }

    @Test
    fun `a malformed neighbor id is bad input, 400, not a refusal`() {
        suspend("not-a-uuid") { manager(it) }.andExpect { status { isBadRequest() } }
    }

    /** Reads the store from the start for a few seconds and returns the Neighbor Suspended events for [neighbor]. */
    private fun suspendedFor(neighbor: NeighborId): List<NeighborSuspended> {
        val found = mutableListOf<NeighborSuspended>()
        val deadline = Instant.now().plus(Duration.ofSeconds(5))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry ->
                            val message: EventMessage = entry.message()
                            if (message.type().name() == NeighborSuspended::class.java.name) {
                                decode(message)
                                    ?.takeIf { it.neighborId == neighbor }
                                    ?.let { found.add(it) }
                            }
                        }
                    }
                    stream.close()
                }
            }.join()
        return found
    }

    private fun decode(message: EventMessage): NeighborSuspended? =
        eventConverter.convertPayload<EventMessage, NeighborSuspended>(message, NeighborSuspended::class.java)
}
