package com.toolshed.web

import com.toolshed.axon.CommandRejected
import org.axonframework.messaging.commandhandling.CommandExecutionException
import org.axonframework.messaging.queryhandling.QueryExecutionException
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

class CommandRejectedAdviceTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(ThrowingController())
            .setControllerAdvice(CommandRejectedAdvice())
            .build()

    @RestController
    class ThrowingController {
        @GetMapping("/direct")
        fun direct(): Unit = throw CommandRejected("INV-TEST-1", "not-shed-manager", "Only the shed manager can add tools.")

        @GetMapping("/command")
        fun command(): Unit =
            throw CommandExecutionException("dispatch failed", CommandRejected("INV-TEST-2", "name-missing", "A tool needs a name."))

        @GetMapping("/query")
        fun query(): Unit =
            throw QueryExecutionException("query failed", CommandRejected("INV-TEST-3", "requester-mismatch", "Not your data."))

        @GetMapping("/unrelated")
        fun unrelated(): Unit = throw CommandExecutionException("dispatch failed", IllegalStateException("boom"))
    }

    @Test
    fun `direct rejection maps to 422 with invariant and reason`() {
        mockMvc.get("/direct") { accept = MediaType.APPLICATION_PROBLEM_JSON }.andExpect {
            status { isEqualTo(422) }
            jsonPath("$.detail") { value("Only the shed manager can add tools.") }
            jsonPath("$.invariant") { value("INV-TEST-1") }
            jsonPath("$.reason") { value("not-shed-manager") }
        }
    }

    @Test
    fun `rejection wrapped in a command execution exception maps to 422`() {
        mockMvc.get("/command") { accept = MediaType.APPLICATION_PROBLEM_JSON }.andExpect {
            status { isEqualTo(422) }
            jsonPath("$.reason") { value("name-missing") }
        }
    }

    @Test
    fun `rejection wrapped in a query execution exception maps to 422`() {
        mockMvc.get("/query") { accept = MediaType.APPLICATION_PROBLEM_JSON }.andExpect {
            status { isEqualTo(422) }
            jsonPath("$.reason") { value("requester-mismatch") }
        }
    }

    @Test
    fun `execution failure without a rejection maps to a generic 500`() {
        mockMvc.get("/unrelated") { accept = MediaType.APPLICATION_PROBLEM_JSON }.andExpect {
            status { isEqualTo(500) }
            jsonPath("$.detail") { value("Request failed") }
        }
    }
}
