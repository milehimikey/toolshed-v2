package com.toolshed.changeemail

import com.toolshed.axon.CommandRejected
import java.text.BreakIterator
import java.time.Instant

/** A refused email change. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class ChangeEmailRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/** The pure decision for Change Email: the command, the folded state, and the moment to record. */
object ChangeEmailDecider {
    /** Pure decision. The checks run in the doc's order; the first that fails is the reason returned. */
    fun decide(
        command: ChangeEmail,
        state: EmailRegistryState,
        changedAt: Instant,
    ): List<NeighborEmailChanged> {
        val neighbor = command.neighborId
        if (!state.isRegistered(neighbor)) refuse("INV-CEM-10", "neighbor-not-found", "There is no such neighbor.")

        val email = command.email?.trim()?.takeIf { it.isNotEmpty() }
        if (email == null) refuse("INV-CEM-11", "email-missing", "Please enter your email address.")
        if (userVisibleLength(email) > 254) {
            refuse("INV-CEM-12", "email-too-long", "Your email address can be at most 254 characters.")
        }
        if (!looksLikeEmail(email)) refuse("INV-CEM-12", "email-invalid", "Please enter a valid email address.")

        // INV-CEM-9: the account's current email, compared exactly as typed, succeeds with nothing recorded.
        if (state.currentEmail(neighbor) == email) return emptyList()
        if (state.isClosed(neighbor)) refuse("INV-CEM-3", "account-closed", "This account is closed.")
        if (state.isSuspended(neighbor)) {
            refuse("INV-CEM-3a", "neighbor-suspended", "You can't change your email while your account is suspended.")
        }
        if (state.isHeldByAnother(email, neighbor)) refuse("INV-CEM-2", "email-in-use", "That email address can't be used.")

        return listOf(NeighborEmailChanged(neighbor, email, changedAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw ChangeEmailRejected(invariant, reason, sentence)

    /** Length in user-visible characters (grapheme clusters), so "e" plus a combining accent counts once. */
    private fun userVisibleLength(text: String): Int {
        val boundaries = BreakIterator.getCharacterInstance()
        boundaries.setText(text)
        var count = 0
        var end = boundaries.next()
        while (end != BreakIterator.DONE) {
            count++
            end = boundaries.next()
        }
        return count
    }

    /** INV-CEM-12: an "@" with at least one character before it and at least one after it. Nothing more. */
    private fun looksLikeEmail(email: String): Boolean {
        val at = email.indexOf('@')
        return at >= 1 && at < email.length - 1
    }
}
