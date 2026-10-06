package com.toolshed.registerneighbor

import com.toolshed.axon.NeighborId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria

/** The consistency boundary for sign-ups: one id for the whole neighborhood. Not an account. */
object NeighborRegistry

/** What an account was registered with. The email is as registered, not as later changed. */
data class Registration(
    val name: String,
    val email: String,
)

/**
 * Folded from the three lifecycle events. Mutable maps, because a neighborhood's history is long and
 * copying on every event would be wasteful.
 */
@EventSourced(idType = NeighborRegistry::class)
class NeighborRegistryState
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
