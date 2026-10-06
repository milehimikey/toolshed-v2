package com.toolshed.cancelhold

import com.toolshed.axon.CommandRejected
import java.time.Instant

/** A refused cancellation. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class CancelHoldRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/** The pure decision for Cancel Hold: the command, the folded state, and the moment to record. */
object CancelHoldDecider {
    /** Pure decision. The checks run in the doc's order; the first that fails is the reason returned. */
    fun decide(
        command: CancelHold,
        state: CancelHoldState,
        cancelledAt: Instant,
    ): List<HoldCancelled> {
        val hold = state.hold(command.holdId) ?: refuse("INV-CNH-7", "hold-not-found", "There is no such hold.")
        if (hold.holder != command.neighborId) {
            refuse("INV-CNH-1", "not-your-hold", "You can only cancel your own holds.")
        }
        // INV-CNH-8: cancelling a hold already cancelled succeeds and records nothing.
        if (hold.status == HoldStatus.CANCELLED) return emptyList()
        if (hold.status == HoldStatus.ENDED) refuse("INV-CNH-3", "hold-ended", "This hold has already ended.")

        return listOf(HoldCancelled(command.holdId, cancelledAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw CancelHoldRejected(invariant, reason, sentence)
}
