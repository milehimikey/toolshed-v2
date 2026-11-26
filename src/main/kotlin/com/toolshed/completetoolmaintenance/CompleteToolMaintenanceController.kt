package com.toolshed.completetoolmaintenance

import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Completing maintenance is a singleton state of the tool (Amendment 10): `PUT /api/tools/{toolId}/maintenance/completion`,
 * answering 204 on the first call and on a repeat. Only the shed manager may do it (Amendment 6). The id is read as text
 * so that sign-in is checked first and a malformed id is bad input (400) only after that, before any rule.
 */
@RestController
@RequestMapping("/api/tools/{toolId}/maintenance/completion")
class CompleteToolMaintenanceController(
    private val dispatcher: CommandDispatcher,
) {
    @PutMapping
    fun complete(
        caller: Caller,
        @PathVariable toolId: String,
    ): ResponseEntity<Void> {
        caller.requireMaintenanceCompleter()
        val id =
            try {
                UUID.fromString(toolId)
            } catch (_: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The tool id is not valid.")
            }
        dispatcher.send(CompleteToolMaintenance(ToolId(id)), Any::class.java)
        return ResponseEntity.noContent().build()
    }
}
