package com.toolshed.sendpickupnotice

import java.time.Instant
import java.time.ZoneId

/**
 * The pure decision for Send Pickup Notice. The checks run in the doc's order; the first that fails is the reason returned.
 * A repeat with the same content succeeds and records nothing (INV-PKN-2).
 */
object SendPickupNoticeDecider {
    fun decide(
        command: SendPickupNotice,
        state: SendPickupNoticeState,
        now: Instant,
        zone: ZoneId,
    ): List<PickupNoticeSent> {
        val sentTo = command.sentTo.trim()
        val holderName = command.holderName.trim()
        val toolName = command.toolName.trim()

        if (!state.exists()) refuse("INV-PKN-9", "hold-not-found", "There is no such hold.")
        state.noticeSent()?.let { prior ->
            val same =
                prior.sentTo == sentTo &&
                    prior.holderName == holderName &&
                    prior.toolName == toolName &&
                    prior.collectByDate == command.collectByDate
            if (same) return emptyList()
            refuse("INV-PKN-2", "notice-already-sent", "A pickup email has already been sent for this hold.")
        }
        if (state.holdEnded()) refuse("INV-PKN-5", "hold-ended", "This hold has already ended.")
        val recordedCollectBy =
            state.collectByDate() ?: refuse("INV-PKN-10", "hold-not-set-aside", "This hold hasn't been set aside.")
        if (command.collectByDate != recordedCollectBy) {
            refuse("INV-PKN-11", "collect-by-date-mismatch", "This isn't the hold's collect-by date.")
        }
        // INV-PKN-12: the set-aside lapses at the start of the day after the collect-by date, shed local time.
        if (now.atZone(zone).toLocalDate().isAfter(recordedCollectBy)) {
            refuse("INV-PKN-12", "pickup-window-ended", "This set-aside's pickup window has ended.")
        }
        return listOf(PickupNoticeSent(command.holdId, sentTo, holderName, toolName, recordedCollectBy, now))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw SendPickupNoticeRejected(invariant, reason, sentence)
}
