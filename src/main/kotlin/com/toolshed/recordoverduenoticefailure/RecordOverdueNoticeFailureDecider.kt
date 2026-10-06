package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.CommandRejected
import java.time.Instant

/** A refused failure report. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class RecordOverdueNoticeFailureRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/** The pure decision for Record Overdue Notice Failure: the command, the folded state, and the moment to record. */
object RecordOverdueNoticeFailureDecider {
    /** Pure decision. The checks run in the doc's order; the first that fails is the reason returned. */
    fun decide(
        command: RecordOverdueNoticeFailure,
        state: NoticeState,
        failedAt: Instant,
    ): List<OverdueNoticeFailed> {
        if (!state.wasNoticeSent(command.loanId)) {
            refuse("INV-ONF-7", "no-matching-notice", "No overdue notice was sent for that loan.")
        }
        // INV-ONF-9: a repeat with the same reason succeeds with nothing recorded.
        state.failureReason(command.loanId)?.let { prior ->
            if (prior == command.reason) return emptyList()
            refuse("INV-ONF-9", "failure-already-recorded", "A failure is already recorded for that notice.")
        }
        if (state.hasEnded(command.loanId)) refuse("INV-ONF-8", "loan-ended", "That loan has already ended.")

        return listOf(OverdueNoticeFailed(command.loanId, command.reason, failedAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw RecordOverdueNoticeFailureRejected(invariant, reason, sentence)
}
