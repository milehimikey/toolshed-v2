package com.toolshed.registerneighbor

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.NeighborId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/** The consistency boundary for sign-ups: one id for the whole neighborhood. Not an account. */
object NeighborRegistry

/** A refused sign-up. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class RegisterNeighborRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/**
 * Register Neighbor (state change). The decider is pure: it takes the command, the state folded
 * from every account-lifecycle event, and the moment to record. The state is loaded through a
 * criteria over all three lifecycle event types, so the append is checked against the same events
 * the decision read; two sign-ups for one email cannot both be recorded.
 */
@Component
class RegisterNeighborCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: RegisterNeighbor,
        @InjectEntity state: State,
        eventAppender: EventAppender,
    ) {
        eventAppender.append(decide(command, state, clock.instant()))
    }

    /** Pure decision. Checks run in the doc's order; the first failure is the one returned. */
    fun decide(
        command: RegisterNeighbor,
        state: State,
        registeredAt: Instant,
    ): List<NeighborRegistered> {
        val name = command.name?.trim()?.takeIf { it.isNotEmpty() }
        val email = command.email?.trim()?.takeIf { it.isNotEmpty() }

        // INV-REG-7 is checked first, so a retry is never refused by its own earlier registration.
        state.registration(command.neighborId)?.let { prior ->
            if (prior.name == name && prior.email.equals(email, ignoreCase = true)) return emptyList()
            refuse(
                "INV-REG-7",
                "neighbor-id-in-use",
                "That neighbor ID is already used by another account.",
            )
        }

        if (name == null) refuse("INV-REG-3", "name-missing", "Please enter your name.")
        if (codePoints(name) > 100) {
            refuse("INV-REG-10", "name-too-long", "Your name can be at most 100 characters.")
        }
        if (email == null) refuse("INV-REG-3", "email-missing", "Please enter your email address.")
        if (codePoints(email) > 254) {
            refuse("INV-REG-10", "email-too-long", "Your email address can be at most 254 characters.")
        }
        if (!looksLikeEmail(email)) {
            refuse("INV-REG-11", "email-invalid", "Please enter a valid email address.")
        }
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

    private fun codePoints(text: String): Int = text.codePointCount(0, text.length)

    /** INV-REG-11: an "@" with at least one character before it and at least one after it. Nothing more. */
    private fun looksLikeEmail(email: String): Boolean {
        val at = email.indexOf('@')
        return at >= 1 && at < email.length - 1
    }

    /**
     * Folded from the three lifecycle events. Mutable maps, because a neighborhood's history is
     * long and copying on every event would be wasteful.
     */
    @EventSourcedEntity
    class State
        @EntityCreator
        constructor() {
            private val registrations = mutableMapOf<NeighborId, Registration>()
            private val currentEmail = mutableMapOf<NeighborId, String>()
            private val holders = mutableMapOf<String, NeighborId>()

            fun registration(neighborId: NeighborId): Registration? = registrations[neighborId]

            /** True when some account holds this email: active, suspended, or closed while suspended. */
            fun isEmailHeld(email: String): Boolean = holders.containsKey(email.lowercase())

            @EventSourcingHandler
            fun evolve(event: NeighborRegistered) {
                registrations[event.neighborId] = Registration(event.name, event.email)
                claim(event.neighborId, event.email)
            }

            @EventSourcingHandler
            fun evolve(event: NeighborEmailChanged) {
                release(event.neighborId)
                claim(event.neighborId, event.email)
            }

            /** Closed while active frees the email; closed while suspended keeps it for good (INV-REG-4). */
            @EventSourcingHandler
            fun evolve(event: NeighborAccountClosed) {
                if (!event.whileSuspended) release(event.neighborId)
            }

            private fun claim(
                neighborId: NeighborId,
                email: String,
            ) {
                val key = email.lowercase()
                currentEmail[neighborId] = key
                holders[key] = neighborId
            }

            private fun release(neighborId: NeighborId) {
                val key = currentEmail.remove(neighborId) ?: return
                if (holders[key] == neighborId) holders.remove(key)
            }

            companion object {
                @EventCriteriaBuilder
                @JvmStatic
                private fun resolveCriteria(registry: NeighborRegistry): EventCriteria =
                    EventCriteria
                        .havingAnyTag()
                        .andBeingOneOfTypes(
                            NeighborRegistered::class.java.name,
                            NeighborEmailChanged::class.java.name,
                            NeighborAccountClosed::class.java.name,
                        )
            }
        }

    /** What an account was registered with. The email is as registered, not as later changed. */
    data class Registration(
        val name: String,
        val email: String,
    )
}
