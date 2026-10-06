package com.toolshed.retiretool

import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Retirement is a singleton state of the tool (Amendment 10), so it is a PUT on the tool's retirement. Only the
 * shed manager may do it (Amendment 6). A repeat of a retired tool answers 204 and records nothing new.
 */
@RestController
@RequestMapping("/api/tools/{toolId}/retirement")
class RetireToolController(
    private val dispatcher: CommandDispatcher,
) {
    @PutMapping
    fun retire(
        @PathVariable toolId: UUID,
        caller: Caller,
    ): ResponseEntity<Void> {
        val command = RetireTool(ToolId(toolId))
        caller.requireRetirer()
        dispatcher.send(command, Any::class.java)
        return ResponseEntity.noContent().build()
    }
}
