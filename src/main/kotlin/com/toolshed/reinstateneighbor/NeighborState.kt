package com.toolshed.reinstateneighbor

import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.suspendneighbor.NeighborSuspended
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator

/** What this decision needs to know about one neighbor, derived from the neighbor's own events. */
data class NeighborFacts(
    val registered: Boolean,
    val suspended: Boolean,
    val closed: Boolean,
)

/** Events for one neighbor, read through its neighborId tag. Other neighborId-tagged events are ignored here. */
@EventSourcedEntity(tagKey = "neighborId")
class NeighborState
    @EntityCreator
    constructor() {
        private var registered = false
        private var suspended = false
        private var closed = false

        val facts: NeighborFacts
            get() = NeighborFacts(registered, suspended, closed)

        @EventSourcingHandler
        fun on(event: NeighborRegistered) {
            registered = true
        }

        @EventSourcingHandler
        fun on(event: NeighborSuspended) {
            suspended = true
        }

        @EventSourcingHandler
        fun on(event: NeighborReinstated) {
            suspended = false
        }

        @EventSourcingHandler
        fun on(event: NeighborAccountClosed) {
            closed = true
        }
    }
