package com.toolshed.retiretool

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria

/**
 * Folded from the tool events the retirement decision reads, and the holds it must end. A lost tool is retired by
 * Tool Lost, so Tool Lost counts as a retirement. A hold is live from Hold Placed until it is cancelled or expired,
 * ended by its holder's checkout or account closing, or its tool is retired or lost. Mutable collections, because
 * the shed's history is long and copying the state on every event would be wasteful.
 */
@EventSourced(idType = ToolLedger::class)
class ToolState
    @EntityCreator
    constructor() {
        private data class LiveHold(
            val toolId: ToolId,
            val holder: NeighborId,
        )

        private val tools = mutableSetOf<ToolId>()
        private val retired = mutableSetOf<ToolId>()
        private val liveHolds = linkedMapOf<HoldId, LiveHold>()

        fun hasTool(toolId: ToolId): Boolean = toolId in tools

        fun isRetired(toolId: ToolId): Boolean = toolId in retired

        /** The holds on the tool still waiting or set aside, in the order they were placed. */
        fun liveHoldsOn(toolId: ToolId): List<HoldId> = liveHolds.filterValues { it.toolId == toolId }.keys.toList()

        @EventSourcingHandler
        fun evolve(event: ToolAdded) {
            tools.add(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolRetired) {
            retired.add(event.toolId)
            liveHolds.values.removeIf { it.toolId == event.toolId }
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            retired.add(event.toolId)
            liveHolds.values.removeIf { it.toolId == event.toolId }
        }

        @EventSourcingHandler
        fun evolve(event: HoldPlaced) {
            liveHolds[event.holdId] = LiveHold(event.toolId, event.neighborId)
        }

        @EventSourcingHandler
        fun evolve(event: HoldCancelled) {
            liveHolds.remove(event.holdId)
        }

        @EventSourcingHandler
        fun evolve(event: HoldExpired) {
            liveHolds.remove(event.holdId)
        }

        /** The holder checking the tool out ends their own hold on it. */
        @EventSourcingHandler
        fun evolve(event: ToolCheckedOut) {
            liveHolds.values.removeIf { it.toolId == event.toolId && it.holder == event.neighborId }
        }

        @EventSourcingHandler
        fun evolve(event: NeighborAccountClosed) {
            liveHolds.values.removeIf { it.holder == event.neighborId }
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            private fun resolveCriteria(ledger: ToolLedger): EventCriteria =
                EventCriteria
                    .havingAnyTag()
                    .andBeingOneOfTypes(
                        ToolAdded::class.java.name,
                        ToolRetired::class.java.name,
                        ToolLost::class.java.name,
                        HoldPlaced::class.java.name,
                        HoldCancelled::class.java.name,
                        HoldExpired::class.java.name,
                        ToolCheckedOut::class.java.name,
                        NeighborAccountClosed::class.java.name,
                    )
        }
    }
