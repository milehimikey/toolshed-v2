package com.toolshed.retiretool

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

private val CANONICAL_UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/**
 * Retirement is a singleton state of the tool (Amendment 10), so it is a PUT on the tool's retirement. Only the
 * shed manager may do it (Amendment 6). A repeat of a retired tool answers 204 and records nothing new.
 *
 * The request is checked in the standing order: not-signed-in (then not-shed-manager) first, whatever else is wrong;
 * then a missing or malformed tool id is bad input (400); then the decider's rules. So the id arrives as text and is
 * parsed after the identity check.
 */
@RestController
@RequestMapping("/api/tools/{toolId}/retirement")
class RetireToolController(
    private val dispatcher: CommandDispatcher,
) {
    @PutMapping
    fun retire(
        @PathVariable toolId: String,
        caller: Caller,
    ): ResponseEntity<Void> {
        caller.requireRetirer()
        if (!CANONICAL_UUID.matches(toolId)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The tool id is not a UUID.")
        }
        dispatcher.send(RetireTool(ToolId(UUID.fromString(toolId))), Any::class.java)
        return ResponseEntity.noContent().build()
    }
}
