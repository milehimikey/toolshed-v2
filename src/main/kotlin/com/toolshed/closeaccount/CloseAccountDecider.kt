package com.toolshed.closeaccount

import java.time.Instant

/**
 * The Close Account decision. Pure: no framework types. The request's sender is checked at the edge, before this runs.
 * Checks run in the doc's order. A repeat, of a closed account, succeeds and records nothing (INV-CLS-2).
 */
object CloseAccountDecider {
    fun decide(
        command: CloseAccount,
        facts: NeighborAccountFacts,
        now: Instant,
    ): List<NeighborAccountClosed> {
        if (!facts.registered) {
            throw CloseAccountRejected("INV-CLS-8", "neighbor-not-found", "There is no such neighbor.")
        }
        if (facts.closed) return emptyList()
        if (facts.openLoans.isNotEmpty()) {
            throw CloseAccountRejected("INV-CLS-7", "tools-on-loan", "Return all your tools before closing your account.")
        }
        return listOf(NeighborAccountClosed(command.neighborId, facts.suspended, now))
    }
}
