package com.toolshed.web

import com.toolshed.axon.CommandRejected
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/** Retry policy over real Axon Server: server failures retry to 3 attempts, refusals never retry. */
@SpringBootTest
@ActiveProfiles("probes")
@AutoConfigureMockMvc
@Testcontainers
class RetrySafetyOverAxonServerTest {
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
    lateinit var handlers: RetryProbeHandlers

    @BeforeEach
    fun resetCounters() {
        handlers.flakyAttempts.set(0)
        handlers.refuseAttempts.set(0)
    }

    @Test
    fun `a refusal is not retried, one attempt and 422`() {
        mockMvc.post("/probe-retry-refuse") { accept = MediaType.APPLICATION_PROBLEM_JSON }.andExpect {
            status { isEqualTo(422) }
            jsonPath("$.reason") { value("probe-refused") }
        }
        assertEquals(1, handlers.refuseAttempts.get())
    }

    @Test
    fun `a persistent server failure is retried to three attempts, then 500`() {
        mockMvc.post("/probe-retry-flaky").andExpect { status { isEqualTo(500) } }
        assertEquals(3, handlers.flakyAttempts.get())
    }
}

data class ProbeRetryFlaky(
    val id: String,
)

data class ProbeRetryRefuse(
    val id: String,
)

@Profile("probes")
@Component
class RetryProbeHandlers {
    val flakyAttempts = AtomicInteger()
    val refuseAttempts = AtomicInteger()

    @CommandHandler
    fun flaky(command: ProbeRetryFlaky) {
        flakyAttempts.incrementAndGet()
        throw IllegalStateException("persistent server failure")
    }

    @CommandHandler
    fun refuse(command: ProbeRetryRefuse) {
        refuseAttempts.incrementAndGet()
        throw object : CommandRejected("INV-PROBE-1", "probe-refused", "Probe refused.") {}
    }
}

@Profile("probes")
@RestController
class RetryProbeController(
    private val dispatcher: CommandDispatcher,
) {
    @PostMapping("/probe-retry-flaky")
    fun flaky() {
        dispatcher.send(ProbeRetryFlaky("x"), Any::class.java)
    }

    @PostMapping("/probe-retry-refuse")
    fun refuse() {
        dispatcher.send(ProbeRetryRefuse("x"), Any::class.java)
    }
}
