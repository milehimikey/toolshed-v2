package com.toolshed.extendloan

import com.toolshed.axon.CommandRejected
import java.time.Instant
import java.time.LocalDate

/** A refused extension. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class ExtendLoanRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/** The pure decision for Extend Loan: the command, the folded state, today in the shed's zone, and the moment to record. */
object ExtendLoanDecider {
    /** Pure decision. The checks run in the doc's order; the first that fails is the one returned. */
    fun decide(
        command: ExtendLoan,
        state: LoanLedgerState,
        today: LocalDate,
        extendedAt: Instant,
    ): List<LoanExtended> {
        val loan = state.loan(command.loanId) ?: refuse("INV-EXT-9", "loan-not-found", "There is no such loan.")
        if (loan.borrower != command.neighborId) {
            refuse("INV-EXT-1", "not-your-loan", "You can only extend your own loans.")
        }
        // INV-EXT-10: a repeat from a due date already extended from succeeds with nothing recorded.
        if (command.fromDueDate in loan.extendedFrom) return emptyList()
        if (loan.ended) refuse("INV-EXT-2", "loan-ended", "This loan has ended.")
        if (command.fromDueDate != loan.dueDate) {
            refuse("INV-EXT-10", "due-date-changed", "This loan's due date has changed. Check it and try again.")
        }
        if (state.isSuspended(loan.borrower)) {
            refuse("INV-EXT-7", "neighbor-suspended", "You can't extend loans while your account is suspended.")
        }
        if (state.isRetired(loan.toolId)) {
            refuse(
                "INV-EXT-8",
                "tool-retired",
                "This tool has been retired, so this loan can't be extended. Please return it by the due date.",
            )
        }
        if (today.isAfter(loan.dueDate)) {
            refuse("INV-EXT-5", "loan-overdue", "This loan is overdue and can't be extended. Please return the tool.")
        }
        if (state.waitingHoldsOn(loan.toolId) > 0) {
            refuse("INV-EXT-4", "hold-waiting", "Someone is waiting for this tool, so this loan can't be extended.")
        }

        val period = checkNotNull(state.defaultPeriodDays(loan.toolId)) { "no default period for tool ${loan.toolId}" }
        return listOf(
            LoanExtended(
                loanId = loan.loanId,
                fromDueDate = command.fromDueDate,
                dueDate = command.fromDueDate.plusDays(period.toLong()),
                extendedAt = extendedAt,
            ),
        )
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw ExtendLoanRejected(invariant, reason, sentence)
}
