package com.toolshed.suspendneighbor

import com.toolshed.axon.CommandRejected
import java.time.Instant

/** A refused suspension. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class SuspendNeighborRefused(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/**
 * The suspension decision, pure. Checks run in the doc's order; a neighbor already suspended records nothing.
 * Identity (not-signed-in, not-shed-manager) is checked by the controller before this runs.
 */
object SuspendNeighborDecider {
    fun decide(
        command: SuspendNeighbor,
        state: NeighborStatusState,
        suspendedAt: Instant,
    ): List<NeighborSuspended> {
        if (!state.registered()) refuse("INV-SUS-10", "neighbor-not-found", "There is no such neighbor.")
        if (state.closed()) refuse("INV-SUS-2", "account-closed", "This account is closed.")
        if (state.suspended()) return emptyList()
        return listOf(NeighborSuspended(command.neighborId, suspendedAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw SuspendNeighborRefused(invariant, reason, sentence)
}
