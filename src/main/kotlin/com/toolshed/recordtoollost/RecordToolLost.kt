package com.toolshed.recordtoollost

import com.toolshed.axon.LoanId
import com.toolshed.axon.ToolId
import org.axonframework.modelling.annotation.TargetEntityId

/**
 * Record Tool Lost: the shed manager records that the tool out on [loanId] is lost. [toolId] is the tool shown on
 * Tool Inventory, from the route; a [toolId] that isn't the loan's own tool names no loan at that URI. No cost or note.
 */
data class RecordToolLost(
    val loanId: LoanId,
    val toolId: ToolId,
) {
    @TargetEntityId
    private fun key() = LoanKey(loanId)
}

/** The entity id for one loan. A data class, because a value-class id is stored as a bare UUID. */
data class LoanKey(
    val loanId: LoanId,
)
