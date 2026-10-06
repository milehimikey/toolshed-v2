package com.toolshed.retiretool

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.ToolId
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
 * Retire Tool over REST, through Spring and a real Axon Server. The tool is seeded through the event gateway.
 * Success is checked by what is recorded. Refusals must come back as 422 ProblemDetails with invariant and reason.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RetireToolEndpointTest {
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

    private val now = Instant.parse("2026-10-06T04:00:00Z")

    private fun seedTool(): ToolId {
        val tool = ToolId(UUID.randomUUID())
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, listOf(ToolAdded(tool, "Ladder", null, 7, now))) }
        uow.execute().join()
        return tool
    }

    private fun retire(
        tool: UUID,
        role: String? = "shed-manager",
        signedIn: Boolean = true,
    ) = mockMvc.put("/api/tools/$tool/retirement") {
        if (signedIn) header("X-Toolshed-Neighbor-Id", UUID.randomUUID().toString())
        role?.let { header("X-Toolshed-Role", it) }
        accept = MediaType.APPLICATION_PROBLEM_JSON
    }

    @Test
    fun `INV-RTR-6 PUT a retirement as the shed manager answers 204 and records one Tool Retired`() {
        val tool = seedTool()

        retire(tool.value).andExpect { status { isNoContent() } }

        assertThat(retiredFor(tool)).hasSize(1)
    }

    @Test
    fun `INV-RTR-2 PUT the same retirement again answers 204 and records nothing new`() {
        val tool = seedTool()

        retire(tool.value).andExpect { status { isNoContent() } }
        retire(tool.value).andExpect { status { isNoContent() } }

        assertThat(retiredFor(tool)).hasSize(1)
    }

    @Test
    fun `INV-RTR-1 PUT a retirement without the shed-manager role answers 422 not-shed-manager`() {
        val tool = seedTool()

        retire(tool.value, role = null).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-RTR-1") }
            jsonPath("$.reason") { value("not-shed-manager") }
            jsonPath("$.detail") { value("Only the shed manager can retire tools.") }
        }
    }

    @Test
    fun `INV-RTR-1 PUT a retirement with no sign-in answers 422 not-signed-in`() {
        val tool = seedTool()

        retire(tool.value, role = null, signedIn = false).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-RTR-1") }
            jsonPath("$.reason") { value("not-signed-in") }
        }
    }

    @Test
    fun `INV-RTR-10 PUT a retirement of an unknown tool answers 422 tool-not-found`() {
        retire(UUID.randomUUID()).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-RTR-10") }
            jsonPath("$.reason") { value("tool-not-found") }
            jsonPath("$.detail") { value("There is no such tool.") }
        }
    }

    /** Reads the store from the start for a few seconds and returns the Tool Retired events for [tool]. */
    private fun retiredFor(tool: ToolId): List<ToolRetired> {
        val found = mutableListOf<ToolRetired>()
        val deadline = Instant.now().plus(Duration.ofSeconds(5))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry ->
                            val message: EventMessage = entry.message()
                            if (message.type().name() == ToolRetired::class.java.name) {
                                decode(message)?.takeIf { it.toolId == tool }?.let { found.add(it) }
                            }
                        }
                    }
                    stream.close()
                }
            }.join()
        return found
    }

    private fun decode(message: EventMessage): ToolRetired? =
        eventConverter.convertPayload<EventMessage, ToolRetired>(message, ToolRetired::class.java)
}
