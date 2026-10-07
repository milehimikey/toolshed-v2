package com.toolshed.addtool

import com.toolshed.axon.ToolId
import com.toolshed.retiretool.ToolRetired
import com.toolshed.testsupport.SharedAxonServer
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
import kotlin.test.assertEquals

/** The endpoint and the real Axon path: guards first, then the decider, against a real Axon Server. */
@SpringBootTest
@AutoConfigureMockMvc
class AddToolEndpointTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun axonServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("axon.axonserver.servers") { SharedAxonServer.address }
        }

        private const val MANAGER = "shed-manager"
    }

    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var eventGateway: EventGateway

    @Autowired lateinit var unitOfWorkFactory: UnitOfWorkFactory

    @Autowired lateinit var eventSource: StreamableEventSource

    @Autowired lateinit var tokenSource: TrackingTokenSource

    @Autowired lateinit var eventConverter: EventConverter

    private fun addTool(
        tool: UUID,
        body: String,
        role: String? = MANAGER,
        neighbor: String? = null,
    ) = mockMvc.put("/api/tools/$tool") {
        contentType = MediaType.APPLICATION_JSON
        content = body
        accept = MediaType.APPLICATION_PROBLEM_JSON
        role?.let { header("X-Toolshed-Role", it) }
        neighbor?.let { header("X-Toolshed-Neighbor-Id", it) }
    }

    private fun body(
        tool: UUID,
        name: String? = "Cordless drill",
        description: String? = "18V, two batteries",
        days: String? = "7",
    ) = """{"name":${name?.let { "\"$it\"" } ?: "null"},""" +
        """"description":${description?.let { "\"$it\"" } ?: "null"},""" +
        """"defaultCheckoutPeriodDays":${days ?: "null"}}"""

    /** Reads the store from the start for [window] and returns the tool-added events for [tool]. */
    private fun addedEventsFor(
        tool: UUID,
        window: Duration = Duration.ofSeconds(3),
    ): List<ToolAdded> {
        val seen = mutableListOf<EventMessage>()
        val deadline = Instant.now().plus(window)
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
            .filter { it.type().name() == ToolAdded::class.java.name }
            .map { checkNotNull(eventConverter.convertPayload<EventMessage, ToolAdded>(it, ToolAdded::class.java)) }
            .filter { it.toolId == ToolId(tool) }
    }

    @Test
    fun `INV-ADD-1 - given no sign-in, when add tool, then refused not-signed-in`() {
        addTool(UUID.randomUUID(), body(UUID.randomUUID()), role = null).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-ADD-1") }
            jsonPath("$.reason") { value("not-signed-in") }
            jsonPath("$.detail") { value("Please sign in.") }
        }
    }

    @Test
    fun `INV-ADD-1 - given a neighbor without the role, when add tool, then refused not-shed-manager`() {
        addTool(UUID.randomUUID(), body(UUID.randomUUID()), role = null, neighbor = UUID.randomUUID().toString()).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-ADD-1") }
            jsonPath("$.reason") { value("not-shed-manager") }
            jsonPath("$.detail") { value("Only the shed manager can add tools.") }
        }
    }

    @Test
    fun `INV-ADD-9 - given no tools, when the shed manager adds a drill, then Tool Added is recorded for that tool id`() {
        val tool = UUID.randomUUID()
        addTool(tool, body(tool)).andExpect { status { isEqualTo(201) } }
        val recorded = addedEventsFor(tool)
        assertEquals(1, recorded.size)
        assertEquals("Cordless drill", recorded.single().name)
        assertEquals("18V, two batteries", recorded.single().description)
        assertEquals(7, recorded.single().defaultCheckoutPeriodDays)
    }

    @Test
    fun `INV-ADD-10 - given the same request arrives twice, when repeated, then succeeds and records nothing new`() {
        val tool = UUID.randomUUID()
        addTool(tool, body(tool)).andExpect { status { isEqualTo(201) } }
        addTool(tool, body(tool)).andExpect { status { isEqualTo(201) } }
        assertEquals(1, addedEventsFor(tool).size)
    }

    @Test
    fun `INV-ADD-10 - given T1 was retired, when the same request arrives again, then succeeds and records nothing`() {
        val tool = UUID.randomUUID()
        addTool(tool, body(tool)).andExpect { status { isEqualTo(201) } }
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, listOf(ToolRetired(ToolId(tool), Instant.now()))) }
        uow.execute().join()
        addTool(tool, body(tool)).andExpect { status { isEqualTo(201) } }
        assertEquals(1, addedEventsFor(tool).size)
    }

    @Test
    fun `INV-ADD-10 - given T1 holds a drill, when T1 is reused for a ladder, then refused tool-id-in-use`() {
        val tool = UUID.randomUUID()
        addTool(tool, body(tool)).andExpect { status { isEqualTo(201) } }
        addTool(tool, body(tool, name = "Ladder", days = "3")).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-ADD-10") }
            jsonPath("$.reason") { value("tool-id-in-use") }
        }
        assertEquals(1, addedEventsFor(tool).size)
    }

    @Test
    fun `INV-ADD-4 - given no name, when add tool, then refused name-missing`() {
        val tool = UUID.randomUUID()
        addTool(tool, body(tool, name = null)).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-ADD-4") }
            jsonPath("$.reason") { value("name-missing") }
        }
    }

    @Test
    fun `INV-ADD-11 - given a 101 character name, when add tool, then refused name-too-long`() {
        addTool(UUID.randomUUID(), body(UUID.randomUUID(), name = "n".repeat(101))).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-ADD-11") }
            jsonPath("$.reason") { value("name-too-long") }
        }
    }

    @Test
    fun `INV-ADD-2 - given no period, when add tool, then refused period-missing`() {
        addTool(UUID.randomUUID(), body(UUID.randomUUID(), days = null)).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-ADD-2") }
            jsonPath("$.reason") { value("period-missing") }
        }
    }

    @Test
    fun `INV-ADD-3 - given a period of two and a half days, when add tool, then refused period-out-of-range`() {
        addTool(UUID.randomUUID(), body(UUID.randomUUID(), days = "2.5")).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-ADD-3") }
            jsonPath("$.reason") { value("period-out-of-range") }
        }
    }

    @Test
    fun `non-numeric period is malformed body, so 400 bad input rather than a refusal`() {
        addTool(UUID.randomUUID(), body(UUID.randomUUID(), days = "\"abc\"")).andExpect {
            status { isEqualTo(400) }
        }
    }

    @Test
    fun `malformed tool id is bad input, so 400`() {
        mockMvc
            .put("/api/tools/not-a-uuid") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Ladder","description":null,"defaultCheckoutPeriodDays":3}"""
            }.andExpect { status { isEqualTo(400) } }
    }
}
