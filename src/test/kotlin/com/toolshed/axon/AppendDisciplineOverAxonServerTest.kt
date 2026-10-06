package com.toolshed.axon

import com.toolshed.retiretool.ToolRetired
import com.toolshed.web.CommandDispatcher
import com.toolshed.web.ProbeRejected
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.EventMessage
import org.axonframework.messaging.eventhandling.conversion.EventConverter
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.messaging.eventstreaming.StreamableEventSource
import org.axonframework.messaging.eventstreaming.StreamingCondition
import org.axonframework.messaging.eventstreaming.TrackingTokenSource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** Refusal records nothing, over real Axon Server: decide-then-append, and append-then-refuse fails closed. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AppendDisciplineOverAxonServerTest {
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

    @Autowired lateinit var unitOfWorkFactory: UnitOfWorkFactory

    @Autowired lateinit var eventSource: StreamableEventSource

    @Autowired lateinit var tokenSource: TrackingTokenSource

    @Autowired lateinit var eventConverter: EventConverter

    /** Stored ToolRetired events for [tool], read from the start of the store over a short window. */
    private fun retiredEventsFor(tool: UUID): Int {
        val seen = mutableListOf<EventMessage>()
        val deadline = Instant.now().plus(Duration.ofSeconds(3))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { seen.add(it.message()) }
                    }
                    stream.close()
                }
            }.join()
        return seen
            .filter { it.type().name() == ToolRetired::class.java.name }
            .map { checkNotNull(eventConverter.convertPayload<EventMessage, ToolRetired>(it, ToolRetired::class.java)) }
            .count { it.toolId == ToolId(tool) }
    }

    @Test
    fun `a refusal in decide records nothing and arrives as 422`() {
        val tool = UUID.randomUUID()
        mockMvc
            .post("/probe-decided?toolId=$tool&refuse=true") { accept = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON }
            .andExpect {
                status { isEqualTo(422) }
                jsonPath("$.invariant") { value("INV-PROBE-1") }
            }
        assertEquals(0, retiredEventsFor(tool))
    }

    @Test
    fun `a decided command appends its events and returns success`() {
        val tool = UUID.randomUUID()
        mockMvc.post("/probe-decided?toolId=$tool&refuse=false").andExpect { status { isEqualTo(204) } }
        assertEquals(1, retiredEventsFor(tool))
    }

    @Test
    fun `an append followed by a refusal fails closed as 500 and records nothing`() {
        val tool = UUID.randomUUID()
        mockMvc.post("/probe-append-refuse?toolId=$tool").andExpect { status { isEqualTo(500) } }
        assertEquals(0, retiredEventsFor(tool))
    }
}

data class ProbeDecided(
    val toolId: UUID,
    val refuse: Boolean,
)

data class ProbeAppendThenRefuse(
    val toolId: UUID,
)

@Component
class ProbeAppendHandlers {
    @CommandHandler
    fun decided(
        command: ProbeDecided,
        context: ProcessingContext,
    ) {
        context.decideAndAppend {
            if (command.refuse) throw ProbeRejected()
            listOf(ToolRetired(ToolId(command.toolId), Instant.now()))
        }
    }

    @CommandHandler
    fun appendThenRefuse(
        command: ProbeAppendThenRefuse,
        context: ProcessingContext,
        appender: EventAppender,
    ) {
        context.putResource(APPENDED, true)
        appender.append(listOf(ToolRetired(ToolId(command.toolId), Instant.now())))
        throw ProbeRejected()
    }
}

@RestController
class AppendProbeController(
    private val dispatcher: CommandDispatcher,
) {
    @PostMapping("/probe-decided")
    fun decided(
        @RequestParam toolId: UUID,
        @RequestParam refuse: Boolean,
    ): ResponseEntity<Void> {
        dispatcher.send(ProbeDecided(toolId, refuse), Any::class.java)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/probe-append-refuse")
    fun appendRefuse(
        @RequestParam toolId: UUID,
    ): ResponseEntity<Void> {
        dispatcher.send(ProbeAppendThenRefuse(toolId), Any::class.java)
        return ResponseEntity.noContent().build()
    }
}
