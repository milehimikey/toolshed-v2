package com.toolshed.web

import io.axoniq.framework.testcontainer.AxonServerContainer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/** A refusal thrown in a handler reaches the client as a 422 with its invariant and reason, over real Axon Server. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CommandRefusalOverAxonServerTest {
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
    fun `a CommandRejected thrown in a handler arrives as 422 with invariant and reason`() {
        mockMvc.post("/probe") { accept = MediaType.APPLICATION_PROBLEM_JSON }.andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-PROBE-1") }
            jsonPath("$.reason") { value("probe-refused") }
            jsonPath("$.detail") { value("Probe refused.") }
        }
    }

    @Test
    fun `a normal command result passes the dispatcher unchanged`() {
        mockMvc.post("/probe-ok").andExpect {
            status { isEqualTo(200) }
            content { string("pong") }
        }
    }
}
