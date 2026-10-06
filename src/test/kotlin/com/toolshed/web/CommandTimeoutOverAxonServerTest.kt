package com.toolshed.web

import io.axoniq.framework.testcontainer.AxonServerContainer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/** A command that answers after the configured timeout is a 504, and is not retried. Over real Axon Server. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("probes")
@TestPropertySource(properties = ["toolshed.command.timeout-seconds=1"])
class CommandTimeoutOverAxonServerTest {
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

    @Test
    fun `a command slower than the timeout is 504`() {
        mockMvc.post("/probe-slow").andExpect { status { isEqualTo(504) } }
    }
}
