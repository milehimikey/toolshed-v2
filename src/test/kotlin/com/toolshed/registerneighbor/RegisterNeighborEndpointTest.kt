package com.toolshed.registerneighbor

import io.axoniq.framework.testcontainer.AxonServerContainer
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.EventMessage
import org.axonframework.messaging.eventhandling.conversion.EventConverter
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
 * Sign-up over REST, through Spring and a real Axon Server. Success is checked by what is recorded.
 * Refusals must come back as 422 ProblemDetails carrying the slice's `invariant` and `reason`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RegisterNeighborEndpointTest {
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
    lateinit var unitOfWorkFactory: UnitOfWorkFactory

    @Autowired
    lateinit var eventSource: StreamableEventSource

    @Autowired
    lateinit var tokenSource: TrackingTokenSource

    @Autowired
    lateinit var eventConverter: EventConverter

    private fun put(
        id: UUID,
        body: String,
    ) = mockMvc.put("/api/neighbors/$id") {
        contentType = MediaType.APPLICATION_JSON
        content = body
        accept = MediaType.APPLICATION_PROBLEM_JSON
    }

    @Test
    fun `INV-REG-5 INV-REG-8 PUT a new sign-up answers 201 and records one Neighbor Registered`() {
        val id = UUID.randomUUID()
        put(id, """{"name":"Ana Ruiz","email":"ana-$id@example.org"}""").andExpect {
            status { isEqualTo(201) }
        }

        val recorded = registeredFor(id)
        assertThat(recorded).hasSize(1)
        assertThat(recorded.single().name).isEqualTo("Ana Ruiz")
    }

    @Test
    fun `INV-REG-7 PUT the same details again answers 201 and records nothing new`() {
        val id = UUID.randomUUID()
        val body = """{"name":"Ana Ruiz","email":"ana-$id@example.org"}"""
        put(id, body).andExpect { status { isEqualTo(201) } }
        put(id, body).andExpect { status { isEqualTo(201) } }

        assertThat(registeredFor(id)).hasSize(1)
    }

    @Test
    fun `INV-REG-7 PUT a used neighbor ID with different details answers 422 neighbor-id-in-use`() {
        // Fails over Axon Server until the foundation fix maps a handler's CommandRejected to 422.
        val id = UUID.randomUUID()
        put(id, """{"name":"Ana Ruiz","email":"ana-$id@example.org"}""").andExpect { status { isEqualTo(201) } }

        put(id, """{"name":"Ben Cole","email":"ben-$id@example.org"}""").andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-REG-7") }
            jsonPath("$.reason") { value("neighbor-id-in-use") }
            jsonPath("$.detail") { value("That neighbor ID is already used by another account.") }
        }
    }

    @Test
    fun `INV-REG-4 PUT an email held by another account answers 422 email-in-use`() {
        // Fails over Axon Server until the foundation fix maps a handler's CommandRejected to 422.
        val email = "shared-${UUID.randomUUID()}@example.org"
        put(UUID.randomUUID(), """{"name":"Ana Ruiz","email":"$email"}""").andExpect { status { isEqualTo(201) } }

        put(UUID.randomUUID(), """{"name":"Ben Cole","email":"$email"}""").andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-REG-4") }
            jsonPath("$.reason") { value("email-in-use") }
        }
    }

    @Test
    fun `INV-REG-3 PUT a blank name answers 422 name-missing`() {
        // Fails over Axon Server until the foundation fix maps a handler's CommandRejected to 422.
        put(UUID.randomUUID(), """{"name":"  ","email":"ana@example.org"}""").andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-REG-3") }
            jsonPath("$.reason") { value("name-missing") }
        }
    }

    /** Reads the store from the start for a few seconds and returns the Neighbor Registered events for [id]. */
    private fun registeredFor(id: UUID): List<NeighborRegistered> {
        val found = mutableListOf<NeighborRegistered>()
        val deadline = Instant.now().plus(Duration.ofSeconds(5))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry ->
                            val message: EventMessage = entry.message()
                            if (message.type().name() == NeighborRegistered::class.java.name) {
                                decode(message)
                                    ?.takeIf { it.neighborId.value == id }
                                    ?.let { found.add(it) }
                            }
                        }
                    }
                    stream.close()
                }
            }.join()
        return found
    }

    private fun decode(message: EventMessage): NeighborRegistered? =
        eventConverter.convertPayload<EventMessage, NeighborRegistered>(message, NeighborRegistered::class.java)
}
