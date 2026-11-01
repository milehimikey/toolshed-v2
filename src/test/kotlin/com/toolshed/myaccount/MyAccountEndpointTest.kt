package com.toolshed.myaccount

import com.toolshed.axon.NeighborId
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
    ) = mockMvc.get("/api/neighbors/$neighbor/account") {
        accept = MediaType.APPLICATION_JSON
        caller?.let { header("X-Toolshed-Neighbor-Id", it) }
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
    fun `INV-MYAC-1 - given no account N9, when N9 asks for their own account, then not found`() {
        read(UUID.randomUUID().toString()).andExpect { status { isNotFound() } }
    }

    @Test
    fun `INV-MYAC-1 INV-MYAC-2 INV-MYAC-3 - given registered Ana, when she reads her account, then name, email and active show`() {
        val ana = NeighborId(UUID.randomUUID())
        publish(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now))

        val deadline = Instant.now().plus(Duration.ofSeconds(15))
        var body = ""
        while (Instant.now().isBefore(deadline)) {
            val response = read(ana.value.toString()).andReturn().response
            body = response.contentAsString
            if (response.status == 200) break
            Thread.sleep(200)
        }
        kotlin.test.assertTrue(body.contains("\"name\":\"Ana Ruiz\""), body)
        kotlin.test.assertTrue(body.contains("\"email\":\"ana@example.org\""), body)
        kotlin.test.assertTrue(body.contains("\"status\":\"active\""), body)
        kotlin.test.assertTrue(body.contains("\"canChangeEmail\":true"), body)
        kotlin.test.assertTrue(body.contains("\"canCloseAccount\":true"), body)
    }
}
