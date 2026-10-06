package com.toolshed.addtool

import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

/** Body for PUT /api/tools/{toolId}. A missing name or period reaches the decider and is refused, not 400. */
data class AddToolRequest(
    val name: String?,
    val description: String?,
    val defaultCheckoutPeriodDays: BigDecimal?,
)

@RestController
class AddToolController(
    private val dispatcher: CommandDispatcher,
) {
    @PutMapping("/api/tools/{toolId}")
    fun addTool(
        caller: Caller,
        @PathVariable toolId: UUID,
        @RequestBody body: AddToolRequest,
    ): ResponseEntity<Void> {
        caller.requireShedManager(
            notSignedIn = Refusal("INV-ADD-1", "Please sign in."),
            notShedManager = Refusal("INV-ADD-1", "Only the shed manager can add tools."),
        )
        dispatcher.send(AddTool(ToolId(toolId), body.name, body.description, body.defaultCheckoutPeriodDays), Any::class.java)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}
