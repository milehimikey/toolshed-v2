package com.toolshed.returntool

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The borrower is named in the path and must be the signed-in neighbor (Amendment 6). Returning ends
 * the loan, so success answers 204, including a repeat that records nothing new.
 */
@RestController
@RequestMapping("/api/neighbors/{neighborId}/loans/{loanId}/return")
class ReturnToolController(
    private val dispatcher: CommandDispatcher,
) {
    @PutMapping
    fun returnTool(
        @PathVariable neighborId: UUID,
        @PathVariable loanId: UUID,
        caller: Caller,
    ): ResponseEntity<Void> {
        val command = ReturnTool(NeighborId(neighborId), LoanId(loanId))
        caller.requireReturner(command)
        dispatcher.send(command, Any::class.java)
        return ResponseEntity.noContent().build()
    }
}
