package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import com.toolshed.registerneighbor.NeighborRegistered
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

/** Neighbors over REST, through Spring and a real Axon Server. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NeighborsEndpointTest {
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
    lateinit var repository: NeighborsRepository

    private fun publish(events: List<Any>) {
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events) }
        uow.execute().join()
    }

    private fun get(
        manager: Boolean,
        neighbor: NeighborId? = null,
    ) = mockMvc.get("/api/neighbors") {
        if (manager) header("X-Toolshed-Role", "shed-manager")
        neighbor?.let { header("X-Toolshed-Neighbor-Id", it.value.toString()) }
        accept = MediaType.APPLICATION_JSON
    }

    @Test
    fun `INV-NBRS-9 INV-NBRS-5 GET as the shed manager answers 200 with the entry and its sign-up date in the shed zone`() {
        val neighbor = NeighborId(UUID.randomUUID())
        // 03:00 UTC on 6 October is still 5 October in America/Denver.
        publish(listOf(NeighborRegistered(neighbor, "Endpoint Ana", "ana@example.org", Instant.parse("2026-10-06T03:00:00Z"))))
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(repository.all().any { it.neighborId == neighbor }).isTrue()
        }

        get(manager = true).andExpect {
            status { isEqualTo(200) }
            jsonPath("$[?(@.neighborId=='${neighbor.value}')].name") { value("Endpoint Ana") }
            jsonPath("$[?(@.neighborId=='${neighbor.value}')].status") { value("active") }
            jsonPath("$[?(@.neighborId=='${neighbor.value}')].canSuspend") { value(true) }
            jsonPath("$[?(@.neighborId=='${neighbor.value}')].signedUpOn") { value("2026-10-05") }
        }
    }

    @Test
    fun `INV-NBRS-9 GET as a neighbor answers 422 not-shed-manager`() {
        get(manager = false, neighbor = NeighborId(UUID.randomUUID())).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-NBRS-9") }
            jsonPath("$.reason") { value("not-shed-manager") }
        }
    }

    @Test
    fun `INV-NBRS-9 GET with no sign-in answers 422 not-signed-in`() {
        get(manager = false).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-NBRS-9") }
            jsonPath("$.reason") { value("not-signed-in") }
        }
    }
}
