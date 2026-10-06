package com.toolshed.changeemail

import com.toolshed.axon.NeighborId
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria

/**
 * Folded from the account events. Mutable maps, because the shed's history is long and copying the whole
 * state on every event would be wasteful. [currentEmail] is kept after closure, so a repeat is still
 * recognised; [holders] is what makes an email unavailable.
 */
@EventSourced(idType = EmailLedger::class)
class EmailRegistryState
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
