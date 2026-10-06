package com.toolshed.registerneighbor

import com.toolshed.axon.CommandRejected
import java.text.BreakIterator
import java.time.Instant

/** A refused sign-up. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class RegisterNeighborRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/**
 * The pure decision for Register Neighbor. Takes the command, the state folded from every
 * account-lifecycle event, and the moment to record; returns the events to append.
 */
object RegisterNeighborDecider {
    /** Pure decision. Checks run in the doc's order; the first failure is the one returned. */
    fun decide(
        command: RegisterNeighbor,
        state: NeighborRegistryState,
        registeredAt: Instant,
    ): List<NeighborRegistered> {
        val name = command.name?.trim()?.takeIf { it.isNotEmpty() }
        val email = command.email?.trim()?.takeIf { it.isNotEmpty() }

        // INV-REG-7 is checked first, so a retry is never refused by its own earlier registration.
        state.registration(command.neighborId)?.let { prior ->
            if (prior.name == name && prior.email.equals(email, ignoreCase = true)) return emptyList()
            refuse("INV-REG-7", "neighbor-id-in-use", "That neighbor ID is already used by another account.")
        }

        if (name == null) refuse("INV-REG-3", "name-missing", "Please enter your name.")
        if (userVisibleLength(name) > 100) {
            refuse("INV-REG-10", "name-too-long", "Your name can be at most 100 characters.")
        }
        if (email == null) refuse("INV-REG-3", "email-missing", "Please enter your email address.")
        if (userVisibleLength(email) > 254) {
            refuse("INV-REG-10", "email-too-long", "Your email address can be at most 254 characters.")
        }
        if (!looksLikeEmail(email)) refuse("INV-REG-11", "email-invalid", "Please enter a valid email address.")
        if (state.isEmailHeld(email)) {
            refuse("INV-REG-4", "email-in-use", "That email address can't be used to sign up.")
        }

        return listOf(NeighborRegistered(command.neighborId, name, email, registeredAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw RegisterNeighborRejected(invariant, reason, sentence)

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

    /** INV-REG-11: an "@" with at least one character before it and at least one after it. Nothing more. */
    private fun looksLikeEmail(email: String): Boolean {
        val at = email.indexOf('@')
        return at >= 1 && at < email.length - 1
    }
}
