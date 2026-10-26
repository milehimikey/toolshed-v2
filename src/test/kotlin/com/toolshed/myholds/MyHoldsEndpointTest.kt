package com.toolshed.myholds

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.placehold.HoldPlaced
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

/**
 * My Holds over REST, through Spring and a real Axon Server. The projection consumes asynchronously, so each
 * seed is awaited against the repository before the GET is asserted (axon-projection-testing's integration level).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MyHoldsEndpointTest {
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
    lateinit var repository: MyHoldsRepository

    private val now = Instant.parse("2026-10-06T04:00:00Z")

    private fun publish(events: List<Any>) {
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events) }
        uow.execute().join()
    }

    private fun get(
        neighborId: NeighborId,
        signedInAs: NeighborId? = neighborId,
    ) = mockMvc.get("/api/neighbors/${neighborId.value}/holds") {
        signedInAs?.let { header("X-Toolshed-Neighbor-Id", it.value.toString()) }
        accept = MediaType.APPLICATION_JSON
    }

    @Test
    fun `GET a neighbor's holds answers 200 with the tool name and position`() {
        val tool = ToolId(UUID.randomUUID())
        val neighbor = NeighborId(UUID.randomUUID())
        val hold = UUID.randomUUID()
        publish(listOf(ToolAdded(tool, "Cordless drill", null, 7, now), HoldPlaced(HoldId(hold), tool, neighbor, now)))

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(repository.holdsForNeighbor(neighbor)).hasSize(1)
        }

        get(neighbor).andExpect {
            status { isEqualTo(200) }
            jsonPath("$[0].toolName") { value("Cordless drill") }
            jsonPath("$[0].status") { value("waiting") }
            jsonPath("$[0].position") { value(1) }
            jsonPath("$[0].canCancel") { value(true) }
        }
    }

    @Test
    fun `GET with no sign-in answers 422 not-signed-in`() {
        val neighbor = NeighborId(UUID.randomUUID())

        get(neighbor, signedInAs = null).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-MYHD-19") }
            jsonPath("$.reason") { value("not-signed-in") }
        }
    }

    @Test
    fun `GET another neighbor's holds answers 422 requester-mismatch`() {
        val neighbor = NeighborId(UUID.randomUUID())
        val someoneElse = NeighborId(UUID.randomUUID())

        get(neighbor, signedInAs = someoneElse).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-MYHD-20") }
            jsonPath("$.reason") { value("requester-mismatch") }
        }
    }
}
