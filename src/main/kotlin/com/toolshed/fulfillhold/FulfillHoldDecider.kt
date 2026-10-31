package com.toolshed.fulfillhold

import java.time.Instant
import java.time.ZoneId

/** The pure decision for Fulfill Hold: the command, the folded state, and the single clock reading. */
object FulfillHoldDecider {
    const val COLLECT_DAYS = 3L

    /** The checks run in the doc's order; the first that fails is the reason returned. */
    fun decide(
        command: FulfillHold,
        state: FulfillState,
        now: Instant,
        zone: ZoneId,
    ): List<HoldFulfilled> {
        val hold = state.hold(command.holdId) ?: refuse("INV-FUL-9", "hold-not-found", "There is no such hold.")
        // INV-FUL-10: a hold already fulfilled succeeds and records nothing, so the collect-by date never moves.
        if (hold.everFulfilled) return emptyList()
        if (hold.ended) refuse("INV-FUL-3", "hold-ended", "This hold has already ended.")
        if (state.isOnLoan(hold.toolId)) refuse("INV-FUL-3", "tool-checked-out", "This tool is already checked out.")
        if (state.isSetAside(hold.toolId)) refuse("INV-FUL-4", "tool-set-aside", "This tool is already set aside for a holder.")
        if (state.frontHold(hold.toolId) != hold.holdId) {
            refuse("INV-FUL-2", "not-front-of-queue", "This hold isn't at the front of its tool's queue.")
        }

        val collectBy = now.atZone(zone).toLocalDate().plusDays(COLLECT_DAYS)
        return listOf(HoldFulfilled(hold.holdId, hold.toolId, hold.holder, collectBy, now))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw FulfillHoldRejected(invariant, reason, sentence)
}
