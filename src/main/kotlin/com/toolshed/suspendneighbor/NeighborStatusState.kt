package com.toolshed.suspendneighbor

import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.messaging.eventstreaming.Tag

/**
 * One neighbor's status, evolved from the events that decide it. Closed is final, and it takes precedence
 * over suspended, because an account closed while suspended has status closed (slices/suspend-neighbor.md).
 */
@EventSourced(idType = NeighborStatusId::class)
class NeighborStatusState
    @EntityCreator
    constructor() {
        private var isRegistered = false
        private var isSuspended = false
        private var isClosed = false

        fun registered(): Boolean = isRegistered

        fun suspended(): Boolean = isSuspended

        fun closed(): Boolean = isClosed

        @EventSourcingHandler
        fun evolve(event: NeighborRegistered) {
            isRegistered = true
        }

        @EventSourcingHandler
        fun evolve(event: NeighborSuspended) {
            isSuspended = true
        }

        @EventSourcingHandler
        fun evolve(event: NeighborReinstated) {
            isSuspended = false
        }

        @EventSourcingHandler
        fun evolve(event: NeighborAccountClosed) {
            isClosed = true
        }

        companion object {
            /** Every event that decides a neighbor's status, by the neighbor's tag. */
            @EventCriteriaBuilder
            @JvmStatic
            fun resolveCriteria(id: NeighborStatusId): EventCriteria =
                EventCriteria
                    .havingTags(Tag.of("neighborId", id.neighborId.value.toString()))
                    .andBeingOneOfTypes(
                        NeighborRegistered::class.java.name,
                        NeighborSuspended::class.java.name,
                        NeighborReinstated::class.java.name,
                        NeighborAccountClosed::class.java.name,
                    )
        }
    }
