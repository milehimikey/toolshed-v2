package com.toolshed.extendloan

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor
import org.axonframework.modelling.annotation.TargetEntityId
import java.time.LocalDate

/**
 * The borrower extending one of their loans. [fromDueDate] is the due date My Loans showed, so a
 * stale screen is refused rather than silently extending from a different date.
 */
data class ExtendLoan(
    val neighborId: NeighborId,
    val loanId: LoanId,
    val fromDueDate: LocalDate,
) {
    @TargetEntityId
    private fun ledger(): LoanLedger = LoanLedger
}

/** The consistency boundary for extensions: one id for the shed's loans and holds. Not a loan. */
object LoanLedger

/** Identity checks that run before the command is sent (Amendment 6): signed in, and the borrower named. */
fun Caller.requireBorrower(command: ExtendLoan) {
    requireNeighbor(
        neighborId = command.neighborId,
        notSignedIn = Refusal("INV-EXT-1", "Please sign in."),
        mismatch = Refusal("INV-EXT-1", "You can only extend loans for yourself."),
    )
}
