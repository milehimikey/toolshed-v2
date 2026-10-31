package com.toolshed.flagneighbor

import com.toolshed.axon.LoanId
import java.time.Instant
import java.time.LocalDate

/**
 * The Flag Neighbor decision. Pure: no framework types. [today] and [now] are one clock reading, taken by the handler.
 * Checks run in the doc's order; the first that fails is the reason returned.
 */
object FlagNeighborDecider {
    const val THRESHOLD = 3
    const val WINDOW_DAYS = 90L

    fun decide(
        command: FlagNeighbor,
        facts: FlagFacts,
        now: Instant,
        today: LocalDate,
    ): List<NeighborFlagged> {
        if (!facts.registered) refuse("INV-FLG-10", "neighbor-not-found", "There is no such neighbor.")
        val named = command.loanIds.toSet()
        // INV-FLG-9: exactly the loans an earlier flag already names succeeds and records nothing.
        if (facts.flaggedSets.any { it == named }) return emptyList()
        if (facts.closed) refuse("INV-FLG-5", "account-closed", "This account is closed.")
        if (facts.suspended) refuse("INV-FLG-5", "neighbor-suspended", "This neighbor is suspended.")

        val oldest = today.minusDays(WINDOW_DAYS - 1)
        val counting: Set<LoanId> =
            facts.lateReturns
                .filter { !it.returnedOn.isBefore(oldest) && !it.returnedOn.isAfter(today) }
                .map { it.loanId }
                .toSet()
        if (counting.size < THRESHOLD) {
            refuse("INV-FLG-2", "below-threshold", "This neighbor has fewer than 3 late returns that count.")
        }
        if (named != counting) {
            refuse("INV-FLG-4", "late-returns-changed", "The late returns behind this flag have changed.")
        }
        return listOf(NeighborFlagged(command.neighborId, command.loanIds.distinct(), now))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw FlagNeighborRejected(invariant, reason, sentence)
}
