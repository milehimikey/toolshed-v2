package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Neighbors view over REST, against a real Axon Server: the Spring-registered projection, then the list. */
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

    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var eventGateway: EventGateway

    @Autowired lateinit var unitOfWorkFactory: UnitOfWorkFactory

    private val now = Instant.parse("2026-10-06T04:00:00Z")

    private fun publish(vararg events: Any) {
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events.toList()) }
        uow.execute().join()
    }

    private fun list(
        role: String? = "shed-manager",
        neighbor: String? = null,
    ) = mockMvc.get("/api/neighbors") {
        accept = MediaType.APPLICATION_JSON
        role?.let { header("X-Toolshed-Role", it) }
        neighbor?.let { header("X-Toolshed-Neighbor-Id", it) }
    }

    /** The view is eventually consistent: poll the list until [check] holds for the rows of [ids], or give up. */
    private fun awaitRows(
        ids: Set<NeighborId>,
        check: (Map<NeighborId, String>) -> Boolean,
    ): String {
        val deadline = Instant.now().plus(Duration.ofSeconds(15))
        var body = ""
        while (Instant.now().isBefore(deadline)) {
            body = list().andReturn().response.contentAsString
            val statuses =
                Regex("\"neighborId\":\"([0-9a-f-]+)\"[^}]*?\"status\":\"(\\w+)\"")
                    .findAll(body)
                    .associate { NeighborId(UUID.fromString(it.groupValues[1])) to it.groupValues[2] }
                    .filterKeys { it in ids }
            if (check(statuses)) return body
            Thread.sleep(200)
        }
        return body
    }

    @Test
    fun `INV-NBRS-9 - given no sign-in, when the list is asked for, then refused not-signed-in`() {
        list(role = null).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-NBRS-9") }
            jsonPath("$.reason") { value("not-signed-in") }
        }
    }

    @Test
    fun `INV-NBRS-9 - given a neighbor without the role, when the list is asked for, then refused not-shed-manager`() {
        list(role = null, neighbor = UUID.randomUUID().toString()).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-NBRS-9") }
            jsonPath("$.reason") { value("not-shed-manager") }
            jsonPath("$.detail") { value("Only the shed manager can see this screen.") }
        }
    }

    @Test
    fun `INV-NBRS-1 INV-NBRS-2 INV-NBRS-3 INV-NBRS-6 - given three accounts, when listed, then Suspend only on active`() {
        val ana = NeighborId(UUID.randomUUID())
        val ben = NeighborId(UUID.randomUUID())
        val cara = NeighborId(UUID.randomUUID())
        publish(
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            NeighborRegistered(ben, "Ben Ito", "ben@example.org", now),
            NeighborRegistered(cara, "Cara Lee", "cara@example.org", now),
            NeighborSuspended(ben, now),
            NeighborAccountClosed(cara, false, now),
        )
        val body = awaitRows(setOf(ana, ben, cara)) { it == mapOf(ana to "active", ben to "suspended", cara to "closed") }
        assertTrue(body.contains("\"canSuspend\":true"), "Ana is active, so Suspend is offered: $body")
    }

    @Test
    fun `INV-NBRS-5 - given Ana changes her email, when the list is read, then the new email is shown`() {
        val ana = NeighborId(UUID.randomUUID())
        publish(
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            NeighborEmailChanged(ana, "ana.ruiz@example.net", now),
        )
        val deadline = Instant.now().plus(Duration.ofSeconds(15))
        var body = ""
        while (Instant.now().isBefore(deadline) && !body.contains("ana.ruiz@example.net")) {
            body = list().andReturn().response.contentAsString
            Thread.sleep(200)
        }
        assertTrue(body.contains("ana.ruiz@example.net"), body)
    }

    @Test
    fun `INV-NBRS-2 - given Ben suspended then reinstated, when the list is read, then Ben is active again`() {
        val ben = NeighborId(UUID.randomUUID())
        publish(
            NeighborRegistered(ben, "Ben Ito", "ben@example.org", now),
            NeighborSuspended(ben, now),
            NeighborReinstated(ben, now),
        )
        awaitRows(setOf(ben)) { it[ben] == "active" }
        assertEquals(
            "active",
            Regex(
                "\"neighborId\":\"${ben.value}\"[^}]*?\"status\":\"(\\w+)\"",
            ).find(list().andReturn().response.contentAsString)?.groupValues?.get(1),
        )
    }
}
