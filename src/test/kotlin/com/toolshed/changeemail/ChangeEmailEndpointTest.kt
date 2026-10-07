package com.toolshed.changeemail

import com.toolshed.axon.NeighborId
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.testsupport.SharedAxonServer
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
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Change Email over REST, through Spring and a real Axon Server. Accounts are seeded through the event
 * gateway. Success is checked by what is recorded. Refusals must come back as 422 ProblemDetails
 * carrying the slice's `invariant` and `reason`.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChangeEmailEndpointTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun axonServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("axon.axonserver.servers") { SharedAxonServer.address }
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

    private val now = Instant.parse("2026-10-06T04:00:00Z")

    /** Two active accounts, Ana and Ben. Fresh ids per test, so each test reads only its own account. */
    private data class Accounts(
        val ana: NeighborId,
        val ben: NeighborId,
    )

    private fun seedAccounts(): Accounts {
        val accounts = Accounts(NeighborId(UUID.randomUUID()), NeighborId(UUID.randomUUID()))
        val events: List<Any> =
            listOf(
                NeighborRegistered(accounts.ana, "Ana Ruiz", "ana-${accounts.ana.value}@example.org", now),
                NeighborRegistered(accounts.ben, "Ben Cole", "ben-${accounts.ben.value}@example.org", now),
            )
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events) }
        uow.execute().join()
        return accounts
    }

    private fun change(
        neighbor: NeighborId,
        body: String,
        signedInAs: NeighborId = neighbor,
    ) = mockMvc.put("/api/neighbors/${neighbor.value}/email") {
        header("X-Toolshed-Neighbor-Id", signedInAs.value.toString())
        contentType = MediaType.APPLICATION_JSON
        content = body
        accept = MediaType.APPLICATION_PROBLEM_JSON
    }

    @Test
    fun `INV-CEM-1 INV-CEM-7 PUT a new email answers 204 and records one Neighbor Email Changed`() {
        val accounts = seedAccounts()
        val email = "ana-new-${accounts.ana.value}@example.org"

        change(accounts.ana, """{"email":"$email"}""").andExpect { status { isNoContent() } }

        val recorded = changedFor(accounts.ana)
        assertThat(recorded).hasSize(1)
        assertThat(recorded.single().email).isEqualTo(email)
    }

    @Test
    fun `INV-CEM-9 PUT the same email again answers 204 and records nothing new`() {
        val accounts = seedAccounts()
        val body = """{"email":"ana-new-${accounts.ana.value}@example.org"}"""

        change(accounts.ana, body).andExpect { status { isNoContent() } }
        change(accounts.ana, body).andExpect { status { isNoContent() } }

        assertThat(changedFor(accounts.ana)).hasSize(1)
    }

    @Test
    fun `INV-CEM-1 PUT a change for Ben while signed in as Ana answers 422 requester-mismatch`() {
        val accounts = seedAccounts()

        change(accounts.ben, """{"email":"x-${accounts.ben.value}@example.org"}""", signedInAs = accounts.ana).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-CEM-1") }
            jsonPath("$.reason") { value("requester-mismatch") }
            jsonPath("$.detail") { value("You can only change your own email.") }
        }
    }

    @Test
    fun `INV-CEM-2 PUT an email held by Ben answers 422 email-in-use`() {
        // Fails over Axon Server until the foundation fix maps a handler's CommandRejected to 422.
        val accounts = seedAccounts()

        change(accounts.ana, """{"email":"ben-${accounts.ben.value}@example.org"}""").andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-CEM-2") }
            jsonPath("$.reason") { value("email-in-use") }
            jsonPath("$.detail") { value("That email address can't be used.") }
        }
    }

    @Test
    fun `INV-CEM-10 PUT a change for an unknown neighbor answers 422 neighbor-not-found`() {
        // Fails over Axon Server until the foundation fix maps a handler's CommandRejected to 422.
        val unknown = NeighborId(UUID.randomUUID())

        change(unknown, """{"email":"x@example.org"}""").andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-CEM-10") }
            jsonPath("$.reason") { value("neighbor-not-found") }
        }
    }

    /** Reads the store from the start for a few seconds and returns the Neighbor Email Changed events for [neighbor]. */
    private fun changedFor(neighbor: NeighborId): List<NeighborEmailChanged> {
        val found = mutableListOf<NeighborEmailChanged>()
        val deadline = Instant.now().plus(Duration.ofSeconds(5))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry ->
                            val message: EventMessage = entry.message()
                            if (message.type().name() == NeighborEmailChanged::class.java.name) {
                                decode(message)?.takeIf { it.neighborId == neighbor }?.let { found.add(it) }
                            }
                        }
                    }
                    stream.close()
                }
            }.join()
        return found
    }

    private fun decode(message: EventMessage): NeighborEmailChanged? =
        eventConverter.convertPayload<EventMessage, NeighborEmailChanged>(message, NeighborEmailChanged::class.java)
}
