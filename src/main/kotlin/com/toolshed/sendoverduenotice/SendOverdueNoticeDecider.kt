package com.toolshed.sendoverduenotice

import java.time.Instant
import java.time.ZoneId

/**
 * The pure decision for Send Overdue Notice. The checks run in the doc's order; the first that fails is the reason returned.
 * A repeat with the same content succeeds and records nothing (INV-OVN-2).
 */
object SendOverdueNoticeDecider {
    fun decide(
        command: SendOverdueNotice,
        state: SendOverdueNoticeState,
        now: Instant,
        zone: ZoneId,
    ): List<OverdueNoticeSent> {
        val sentTo = command.sentTo.trim()
        val borrowerName = command.borrowerName.trim()
        val toolName = command.toolName.trim()

        if (!state.exists()) refuse("INV-OVN-10", "loan-not-found", "There is no such loan.")
        state.noticeSent()?.let { prior ->
            val same =
                prior.sentTo == sentTo &&
                    prior.borrowerName == borrowerName &&
                    prior.toolName == toolName &&
                    prior.dueDate == command.dueDate
            if (same) return emptyList()
            refuse("INV-OVN-2", "notice-already-sent", "An overdue notice has already been sent for this loan.")
        }
        if (state.loanEnded()) refuse("INV-OVN-5", "loan-ended", "This loan has ended.")
        val currentDue = state.dueDate() ?: refuse("INV-OVN-10", "loan-not-found", "There is no such loan.")
        if (command.dueDate != currentDue) refuse("INV-OVN-11", "due-date-changed", "This loan's due date has changed.")
        // INV-OVN-6: overdue from the start of the day after the due date, shed local time.
        if (!now.atZone(zone).toLocalDate().isAfter(currentDue)) refuse("INV-OVN-6", "loan-not-overdue", "This loan isn't overdue.")
        return listOf(OverdueNoticeSent(command.loanId, sentTo, borrowerName, toolName, currentDue, now))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw SendOverdueNoticeRejected(invariant, reason, sentence)
}
