package com.toolshed.changeemail

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.NeighborId
import com.toolshed.axon.decideAndAppend
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.text.BreakIterator
import java.time.Clock
import java.time.Instant

private val logger = KotlinLogging.logger {}

/** A refused email change. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class ChangeEmailRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/**
 * Change Email (state change). The decider is pure: the command, the state folded from the account
 * events (registration, email change, suspension, reinstatement, closure), and the moment to record.
 * One criteria over those events makes the email check and the append one boundary, so two accounts
 * can never be given the same email.
 */
@Component
class ChangeEmailCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: ChangeEmail,
        @InjectEntity state: State,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                context.decideAndAppend { decide(command, state, clock.instant()) }
            } catch (refused: ChangeEmailRejected) {
                logger.info { "change-email neighborId=${command.neighborId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "change-email neighborId=${command.neighborId.value} outcome=$outcome" }
    }

    /** Pure decision. The checks run in the doc's order; the first that fails is the reason returned. */
    fun decide(
        command: ChangeEmail,
        state: State,
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

    /**
     * Folded from the account events. Mutable maps, because the shed's history is long and copying the
     * whole state on every event would be wasteful. [currentEmail] is kept after closure, so a repeat is
     * still recognised; [holders] is what makes an email unavailable.
     */
    @EventSourced(idType = EmailLedger::class)
    class State
        @EntityCreator
        constructor() {
            private val registered = mutableSetOf<NeighborId>()
            private val suspended = mutableSetOf<NeighborId>()
            private val closed = mutableSetOf<NeighborId>()
            private val currentEmail = mutableMapOf<NeighborId, String>()
            private val holders = mutableMapOf<String, NeighborId>()

            fun isRegistered(neighborId: NeighborId): Boolean = neighborId in registered

            fun isSuspended(neighborId: NeighborId): Boolean = neighborId in suspended

            fun isClosed(neighborId: NeighborId): Boolean = neighborId in closed

            fun currentEmail(neighborId: NeighborId): String? = currentEmail[neighborId]

            /** True when another account holds the email (ignoring case). An account's own email never counts. */
            fun isHeldByAnother(
                email: String,
                neighborId: NeighborId,
            ): Boolean {
                val holder = holders[email.lowercase()] ?: return false
                return holder != neighborId
            }

            @EventSourcingHandler
            fun evolve(event: NeighborRegistered) {
                registered.add(event.neighborId)
                claim(event.neighborId, event.email)
            }

            @EventSourcingHandler
            fun evolve(event: NeighborEmailChanged) {
                releaseHolder(event.neighborId)
                claim(event.neighborId, event.email)
            }

            @EventSourcingHandler
            fun evolve(event: NeighborSuspended) {
                suspended.add(event.neighborId)
            }

            @EventSourcingHandler
            fun evolve(event: NeighborReinstated) {
                suspended.remove(event.neighborId)
            }

            /** Closed while active frees the email; closed while suspended keeps it (INV-CEM-2). */
            @EventSourcingHandler
            fun evolve(event: NeighborAccountClosed) {
                closed.add(event.neighborId)
                if (!event.whileSuspended) releaseHolder(event.neighborId)
            }

            private fun claim(
                neighborId: NeighborId,
                email: String,
            ) {
                currentEmail[neighborId] = email
                holders[email.lowercase()] = neighborId
            }

            private fun releaseHolder(neighborId: NeighborId) {
                val key = currentEmail[neighborId]?.lowercase() ?: return
                if (holders[key] == neighborId) holders.remove(key)
            }

            companion object {
                @EventCriteriaBuilder
                @JvmStatic
                private fun resolveCriteria(ledger: EmailLedger): EventCriteria =
                    EventCriteria
                        .havingAnyTag()
                        .andBeingOneOfTypes(
                            NeighborRegistered::class.java.name,
                            NeighborEmailChanged::class.java.name,
                            NeighborSuspended::class.java.name,
                            NeighborReinstated::class.java.name,
                            NeighborAccountClosed::class.java.name,
                        )
            }
        }
}
