package com.toolshed.checkouttool

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.net.URI
import java.util.UUID

/**
 * Check Out Tool over REST: `POST /loans`. The loan ID is chosen by the caller, so a retried
 * request is recognised. A repeat that succeeds records nothing and still answers `201`.
 */
@RestController
class CheckOutToolController(
    private val commandGateway: CommandGateway,
) {
    @PostMapping("/loans")
    fun checkOut(
        caller: Caller,
        @RequestBody request: CheckOutToolRequest,
    ): ResponseEntity<Void> {
        val neighbor = NeighborId(request.neighborId)
        caller.requireNeighbor(
            neighborId = neighbor,
            notSignedIn = Refusal("INV-CHK-1", "Please sign in."),
            mismatch = Refusal("INV-CHK-1", "You can only check out tools for yourself."),
        )
        commandGateway.sendAndWait(
            CheckOutTool(
                loanId = LoanId(request.loanId),
                toolId = ToolId(request.toolId),
                neighborId = neighbor,
                loanPeriodDays = request.loanPeriodDays,
            ),
        )
        return ResponseEntity.created(URI.create("/loans/${request.loanId}")).build()
    }
}

data class CheckOutToolRequest(
    val loanId: UUID,
    val toolId: UUID,
    val neighborId: UUID,
    val loanPeriodDays: BigDecimal? = null,
)
