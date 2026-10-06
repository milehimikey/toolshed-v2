package com.toolshed.recordpickupnoticefailure

import com.toolshed.axon.CommandRejected
import java.time.Instant

/** A refused failure report. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class RecordPickupNoticeFailureRefused(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/**
 * The pickup-failure decision, pure. Checks run in the doc's order and the first failure is the refusal. A repeat
 * with the same reason succeeds with nothing recorded, even after the set-aside has ended.
 */
object RecordPickupNoticeFailureDecider {
    fun decide(
        command: RecordPickupNoticeFailure,
        state: PickupNoticeState,
        failedAt: Instant,
    ): List<PickupNoticeFailed> {
        if (!state.noticeSent(command.holdId)) {
            refuse("INV-PNF-8", "no-matching-notice", "No pickup email was sent for that hold.")
        }
        state.failureReason(command.holdId)?.let { prior ->
            if (prior == command.reason) return emptyList()
            refuse("INV-PNF-10", "failure-already-recorded", "A failure is already recorded for that email.")
        }
        if (state.holdEnded(command.holdId)) refuse("INV-PNF-9", "hold-ended", "This hold has already ended.")
        return listOf(PickupNoticeFailed(command.holdId, command.reason, failedAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw RecordPickupNoticeFailureRefused(invariant, reason, sentence)
}
