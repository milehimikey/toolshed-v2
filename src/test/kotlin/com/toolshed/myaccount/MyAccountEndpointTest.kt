package com.toolshed.myaccount

import com.toolshed.axon.NeighborId
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
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
import kotlin.test.assertTrue

/** My Account over REST against a real Axon Server: the Spring-registered projection, the query, and the identity refusals. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MyAccountEndpointTest {
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
    ) = mockMvc.get("/api/my-account/$neighbor") {
        accept = MediaType.APPLICATION_JSON
        caller?.let { header("X-Toolshed-Neighbor-Id", it) }
    }

    /** The view is eventually consistent: poll the account until [check] holds on its body, or give up. */
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
    fun `INV-MYAC-13 - given no sign-in, when My Account is asked for, then refused not-signed-in`() {
        read(UUID.randomUUID().toString(), caller = null).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-MYAC-13") }
            jsonPath("$.reason") { value("not-signed-in") }
            jsonPath("$.detail") { value("Please sign in.") }
        }
    }

    @Test
    fun `INV-MYAC-14 - given Ben signed in, when Ana's account is asked for, then refused requester-mismatch`() {
        read(UUID.randomUUID().toString(), caller = UUID.randomUUID().toString()).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-MYAC-14") }
            jsonPath("$.reason") { value("requester-mismatch") }
            jsonPath("$.detail") { value("You can only see your own account.") }
        }
    }

    @Test
    fun `INV-MYAC-1 INV-MYAC-3 INV-MYAC-9 INV-MYAC-11 - given Ana registered, when she reads it, then active with both actions`() {
        val ana = NeighborId(UUID.randomUUID())
        publish(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now))
        val body = awaitBody(ana) { it.contains("\"status\":\"active\"") }
        assertTrue(body.contains("\"canChangeEmail\":true") && body.contains("\"canCloseAccount\":true"), body)
    }

    @Test
    fun `INV-MYAC-5 INV-MYAC-6 - given Ana closed her account, when she reads it, then it is closed and still readable with no actions`() {
        val ana = NeighborId(UUID.randomUUID())
        publish(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now), NeighborAccountClosed(ana, false, now))
        val body = awaitBody(ana) { it.contains("\"status\":\"closed\"") }
        assertTrue(body.contains("\"status\":\"closed\"") && body.contains("\"canChangeEmail\":false"), body)
    }

    @Test
    fun `INV-MYAC-1 - given no account N9, when asked for by N9, then not found`() {
        val n9 = UUID.randomUUID().toString()
        read(n9).andExpect {
            status { isEqualTo(404) }
        }
    }

    @Test
    fun `INV-MYAC-1 - given a malformed neighbor id, when asked for, then bad input 400`() {
        mockMvc
            .get("/api/my-account/not-a-uuid") { header("X-Toolshed-Neighbor-Id", "not-a-uuid") }
            .andExpect { status { isEqualTo(400) } }
    }
}
