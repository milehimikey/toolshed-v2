package com.toolshed.reinstateneighbor

import java.time.Instant

private const val INV_NOT_FOUND = "INV-REI-8"
private const val INV_CLOSED = "INV-REI-2"

/**
 * The Reinstate Neighbor decision. Pure: no framework types. Identity is checked at the edge, before this runs.
 * Checks run in the doc's order. Closed is tested before suspended: a closed account is refused whatever its last status.
 */
object ReinstateNeighborDecider {
    fun decide(
        command: ReinstateNeighbor,
        facts: NeighborFacts,
        now: Instant,
    ): List<NeighborReinstated> {
        if (!facts.registered) {
            throw ReinstateNeighborRejected(INV_NOT_FOUND, "neighbor-not-found", "There is no such neighbor.")
        }
        if (facts.closed) {
            throw ReinstateNeighborRejected(INV_CLOSED, "account-closed", "This account is closed.")
        }
        // Already active, or never suspended: succeeds and records nothing (INV-REI-7).
        if (!facts.suspended) return emptyList()
        return listOf(NeighborReinstated(command.neighborId, now))
    }
}
