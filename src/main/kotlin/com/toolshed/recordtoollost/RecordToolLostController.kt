package com.toolshed.recordtoollost

import com.toolshed.axon.LoanId
import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Record Tool Lost over REST: `PUT /api/tools/{toolId}/loans/{loanId}/lost`, from the shed manager only. The loan is
 * the singleton being marked; a toolId that isn't the loan's tool names no loan at that URI and is refused
 * loan-not-found. A repeat for an already-lost loan succeeds and records nothing, and still answers `204`.
 */
@RestController
class RecordToolLostController(
    private val dispatcher: CommandDispatcher,
) {
    @PutMapping("/api/tools/{toolId}/loans/{loanId}/lost")
    fun recordLost(
        caller: Caller,
        @PathVariable toolId: UUID,
        @PathVariable loanId: UUID,
    ): ResponseEntity<Void> {
        caller.requireShedManager(
            notSignedIn = Refusal("INV-LOST-12", "Please sign in."),
            notShedManager = Refusal("INV-LOST-1", "Only the shed manager can record tools lost."),
        )
        dispatcher.send(RecordToolLost(LoanId(loanId), ToolId(toolId)), Any::class.java)
        return ResponseEntity.noContent().build()
    }
}
