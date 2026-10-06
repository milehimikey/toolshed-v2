package com.toolshed.placehold

import com.toolshed.axon.CommandRejected
import java.time.Instant

/** A refused hold. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class PlaceHoldRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/** The pure decision for Place Hold: the command, the folded state, and the moment to record. */
object PlaceHoldDecider {
    /** Pure decision. The checks run in the doc's order; the first that fails is the reason returned. */
    fun decide(
        command: PlaceHold,
        state: HoldState,
        placedAt: Instant,
    ): List<HoldPlaced> {
        // INV-HOLD-13: a repeat with the same tool and neighbor succeeds with nothing recorded.
        state.placement(command.holdId)?.let { prior ->
            if (prior.toolId == command.toolId && prior.neighborId == command.neighborId) return emptyList()
            refuse("INV-HOLD-13", "hold-id-in-use", "That hold ID is already used by another hold.")
        }
        if (!state.hasTool(command.toolId)) refuse("INV-HOLD-14", "tool-not-found", "There is no such tool.")
        if (!state.isRegistered(command.neighborId)) {
            refuse("INV-HOLD-14", "neighbor-not-found", "There is no such neighbor.")
        }
        if (state.isRetired(command.toolId)) refuse("INV-HOLD-3", "tool-retired", "This tool has been retired.")
        if (state.isClosed(command.neighborId)) refuse("INV-HOLD-4", "account-closed", "This account is closed.")
        if (state.isSuspended(command.neighborId)) {
            refuse("INV-HOLD-9", "neighbor-suspended", "You can't place holds while your account is suspended.")
        }
        if (state.hasOpenLoan(command.toolId, command.neighborId)) {
            refuse("INV-HOLD-8", "tool-on-loan-to-you", "You have this tool on loan.")
        }
        if (state.hasActiveHold(command.toolId, command.neighborId)) {
            refuse("INV-HOLD-7", "already-holding", "You already have a hold on this tool.")
        }
        if (state.isAvailable(command.toolId)) {
            refuse("INV-HOLD-2", "tool-available", "This tool is available. Check it out instead.")
        }

        return listOf(HoldPlaced(command.holdId, command.toolId, command.neighborId, placedAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw PlaceHoldRejected(invariant, reason, sentence)
}
