package com.toolshed.suspendedneighbors

import com.toolshed.axon.NeighborId
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.suspendneighbor.NeighborSuspended
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
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

/** Suspended Neighbors over REST, through Spring and a real Axon Server. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SuspendedNeighborsEndpointTest {
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
    lateinit var repository: SuspendedNeighborsRepository

    private val now = Instant.parse("2026-10-07T04:00:00Z")

    private fun publish(events: List<Any>) {
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events) }
        uow.execute().join()
    }

    private fun get(
        manager: Boolean,
        neighbor: NeighborId? = null,
    ) = mockMvc.get("/api/suspended-neighbors") {
        if (manager) header("X-Toolshed-Role", "shed-manager")
        neighbor?.let { header("X-Toolshed-Neighbor-Id", it.value.toString()) }
        accept = MediaType.APPLICATION_JSON
    }

    @Test
    fun `INV-SPN-11 GET as the shed manager answers 200 with the entry`() {
        val neighbor = NeighborId(UUID.randomUUID())
        publish(
            listOf(
                NeighborRegistered(neighbor, "Endpoint Ana", "ana@example.org", now),
                NeighborSuspended(neighbor, now),
            ),
        )
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(repository.entries().any { it.neighborId == neighbor }).isTrue()
        }

        get(manager = true).andExpect {
            status { isEqualTo(200) }
            jsonPath("$[?(@.neighborId=='${neighbor.value}')].name") { value("Endpoint Ana") }
            jsonPath("$[?(@.neighborId=='${neighbor.value}')].email") { value("ana@example.org") }
        }
    }

    @Test
    fun `INV-SPN-11 GET as a neighbor answers 422 not-shed-manager`() {
        get(manager = false, neighbor = NeighborId(UUID.randomUUID())).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-SPN-11") }
            jsonPath("$.reason") { value("not-shed-manager") }
        }
    }

    @Test
    fun `INV-SPN-11 GET with no sign-in answers 422 not-signed-in`() {
        get(manager = false).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-SPN-11") }
            jsonPath("$.reason") { value("not-signed-in") }
        }
    }
}
