package com.toolshed.returntool

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor
import org.axonframework.modelling.annotation.TargetEntityId

/** A borrower returning one of their own loans. The loan's tool and due date come from its events. */
data class ReturnTool(
    val neighborId: NeighborId,
    val loanId: LoanId,
) {
    @TargetEntityId
    private fun ledger(): LoanBook = LoanBook
}

/** The consistency boundary for returns: one id for the shed's loans. Not a loan. */
object LoanBook

/** Identity checks that run before the command is sent (Amendment 6): signed in, and the borrower named. */
fun Caller.requireReturner(command: ReturnTool) {
    requireNeighbor(
        neighborId = command.neighborId,
        notSignedIn = Refusal("INV-RET-12", "Please sign in."),
        mismatch = Refusal("INV-RET-1", "You can only return loans for yourself."),
    )
}
