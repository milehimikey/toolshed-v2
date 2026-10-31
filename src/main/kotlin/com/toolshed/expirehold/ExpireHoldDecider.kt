package com.toolshed.expirehold

import java.time.Instant
import java.time.ZoneId

/** The pure decision for Expire Hold: the command, the folded state, and the single clock reading. */
object ExpireHoldDecider {
    /** The checks run in the doc's order; the first that fails is the reason returned. */
    fun decide(
        command: ExpireHold,
        state: ExpireState,
        now: Instant,
        zone: ZoneId,
    ): List<HoldExpired> {
        val hold = state.hold(command.holdId) ?: refuse("INV-EXP-10", "hold-not-found", "There is no such hold.")
        // INV-EXP-11: a hold already expired succeeds and records nothing.
        if (hold.expired) return emptyList()
        if (hold.ended) refuse("INV-EXP-3", "hold-ended", "This hold has already ended.")
        val collectBy = hold.collectByDate ?: refuse("INV-EXP-10", "hold-not-set-aside", "This hold hasn't been set aside.")
        // INV-EXP-2: expirable from the start of the day after the collect-by date, shed time. INV-EXP-4: nothing about the holder.
        val today = now.atZone(zone).toLocalDate()
        if (!today.isAfter(collectBy)) refuse("INV-EXP-2", "pickup-window-open", "This hold's pickup window hasn't ended.")
        return listOf(HoldExpired(hold.holdId, now))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw ExpireHoldRejected(invariant, reason, sentence)
}
