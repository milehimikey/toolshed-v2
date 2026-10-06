package com.toolshed.checkouttool

import com.toolshed.axon.CommandRejected
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** A refused checkout. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class CheckOutRefused(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/**
 * The checkout decision, pure: no framework types. Checks run in the doc's order and the first
 * failure is the refusal. A repeat of an already-recorded loan returns no events.
 */
object CheckOutDecider {
    fun decide(
        command: CheckOutTool,
        state: CheckOutState,
        today: LocalDate,
        checkedOutAt: Instant,
    ): List<ToolCheckedOut> {
        // 2. A loan ID already in use: a repeat with the same details succeeds, otherwise refused.
        state.loan(command.loanId)?.let { existing ->
            val same =
                existing.toolId == command.toolId &&
                    existing.neighborId == command.neighborId &&
                    requestedDays(command, state.defaultPeriodOf(existing.toolId)) == existing.loanPeriodDays
            if (same) return emptyList()
            refuse("INV-CHK-12", "loan-id-in-use", "That loan ID is already used by another loan.")
        }

        // 3. The tool and the borrower must exist.
        if (!state.hasTool(command.toolId)) refuse("INV-CHK-11", "tool-not-found", "There is no such tool.")
        if (!state.hasNeighbor(command.neighborId)) refuse("INV-CHK-11", "neighbor-not-found", "There is no such neighbor.")

        // 4. A retired tool, including one recorded lost.
        if (state.isRetired(command.toolId)) refuse("INV-CHK-4", "tool-retired", "This tool has been retired.")

        // 5. Already on loan.
        if (state.openLoansOf(command.toolId).isNotEmpty()) {
            refuse("INV-CHK-2", "tool-checked-out", "This tool is already checked out.")
        }

        // 6. Closed accounts, then suspended ones.
        if (state.isClosed(command.neighborId)) refuse("INV-CHK-7", "account-closed", "This account is closed.")
        if (state.isSuspended(command.neighborId)) {
            refuse("INV-CHK-7", "neighbor-suspended", "You can't borrow tools while your account is suspended.")
        }

        // 7. An open loan due before today. A loan due today is not overdue.
        if (state.openLoansOf(command.neighborId).any { it.dueDate.isBefore(today) }) {
            refuse("INV-CHK-5", "has-overdue-loan", "You have an overdue loan. Return it before borrowing another tool.")
        }

        // 8. Holds. A set-aside holds the tool through its collect-by date; after that it has lapsed.
        checkHolds(command, state, today)

        // 9. The loan period: absent means the default; otherwise a whole number from 1 up to the default.
        val defaultDays = state.defaultPeriodOf(command.toolId) ?: 0
        val days = requestedDays(command, defaultDays)
        if (days == null || days !in 1..defaultDays) {
            refuse(
                "INV-CHK-6",
                "period-out-of-range",
                "The loan period must be a whole number of days from 1 up to this tool's default checkout period.",
            )
        }

        return listOf(
            ToolCheckedOut(
                loanId = command.loanId,
                toolId = command.toolId,
                neighborId = command.neighborId,
                loanPeriodDays = days,
                dueDate = today.plusDays(days.toLong()),
                checkedOutAt = checkedOutAt,
            ),
        )
    }

    private fun checkHolds(
        command: CheckOutTool,
        state: CheckOutState,
        today: LocalDate,
    ) {
        val setAside = state.setAsideHolds(command.toolId).lastOrNull()
        val collectBy = setAside?.collectByDate
        if (setAside != null && collectBy != null && !collectBy.isBefore(today)) {
            if (setAside.neighborId != command.neighborId) {
                refuse("INV-CHK-3", "set-aside-for-another", "This tool is set aside for another neighbor.")
            }
            return
        }

        // The set-aside, if any, has lapsed. A hold still waiting takes the tool next.
        val waiting = state.waitingHolds(command.toolId)
        if (waiting.isEmpty()) return
        if (setAside != null && setAside.neighborId == command.neighborId) {
            refuse(
                "INV-CHK-3b",
                "pickup-window-ended",
                "Your pickup window has ended, and this tool has passed to the next neighbor in the queue.",
            )
        }
        if (waiting.first().neighborId != command.neighborId) {
            refuse("INV-CHK-3a", "reserved-for-another", "This tool is reserved for the next neighbor in the hold queue.")
        }
    }

    /** The period asked for: the default when absent, else the whole number of days, or null if fractional or out of range. */
    private fun requestedDays(
        command: CheckOutTool,
        defaultDays: Int?,
    ): Int? {
        val asked: BigDecimal = command.loanPeriodDays ?: return defaultDays
        return try {
            asked.intValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw CheckOutRefused(invariant, reason, sentence)
}
