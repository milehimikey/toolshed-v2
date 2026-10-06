package com.toolshed.returntool

import com.toolshed.axon.CommandRejected
import java.time.Instant
import java.time.LocalDate

/** A refused return. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class ReturnToolRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/** The pure decision for Return Tool: the command, the folded state, the shed day, and the moment to record. */
object ReturnToolDecider {
    /**
     * Pure decision. The checks run in the doc's order; the first that fails is the reason returned.
     * `returnedOn` and `late` are worked out here, once, so every view reads the same answer (INV-RET-4).
     */
    fun decide(
        command: ReturnTool,
        state: LoanBookState,
        today: LocalDate,
        returnedAt: Instant,
    ): List<ToolReturned> {
        val loan = state.loan(command.loanId) ?: refuse("INV-RET-11", "loan-not-found", "There is no such loan.")
        if (loan.borrower != command.neighborId) {
            refuse("INV-RET-1", "not-your-loan", "You can only return your own loans.")
        }
        // INV-RET-10: returning a loan already returned by this borrower succeeds with nothing recorded.
        if (loan.returned) return emptyList()
        if (loan.lost) refuse("INV-RET-2", "loan-ended", "This loan has ended.")

        // INV-RET-4: late from the start of the day after the due date in force at the moment of return.
        val late = today.isAfter(loan.dueDate)
        return listOf(ToolReturned(command.loanId, loan.toolId, returnedAt, today, late))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw ReturnToolRejected(invariant, reason, sentence)
}
