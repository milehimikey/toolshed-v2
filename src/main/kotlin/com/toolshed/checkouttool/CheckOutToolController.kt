package com.toolshed.checkouttool

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.net.URI
import java.util.UUID

/**
 * Check Out Tool over REST: `PUT /api/neighbors/{neighborId}/loans/{loanId}`. The loan ID is chosen by the caller,
 * so PUT is the repeat-safe create: a repeat with the same details succeeds, records nothing, and answers `201`.
 */
@RestController
class CheckOutToolController(
    private val dispatcher: CommandDispatcher,
) {
    @PutMapping("/api/neighbors/{neighborId}/loans/{loanId}")
    fun checkOut(
        caller: Caller,
        @PathVariable neighborId: UUID,
        @PathVariable loanId: UUID,
        @RequestBody request: CheckOutToolRequest,
    ): ResponseEntity<Void> {
        val neighbor = NeighborId(neighborId)
        caller.requireNeighbor(
            neighborId = neighbor,
            notSignedIn = Refusal("INV-CHK-1", "Please sign in."),
            mismatch = Refusal("INV-CHK-1", "You can only check out tools for yourself."),
        )
        dispatcher.send(
            CheckOutTool(
                loanId = LoanId(loanId),
                toolId = ToolId(request.toolId),
                neighborId = neighbor,
                loanPeriodDays = request.loanPeriodDays,
            ),
            Any::class.java,
        )
        return ResponseEntity.created(URI.create("/api/neighbors/$neighborId/loans/$loanId")).build()
    }
}

/** The body names the tool and the optional period; the loan and the neighbor are in the path. */
data class CheckOutToolRequest(
    val toolId: UUID,
    val loanPeriodDays: BigDecimal? = null,
)
