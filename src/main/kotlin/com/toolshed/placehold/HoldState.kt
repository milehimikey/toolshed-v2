package com.toolshed.placehold

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria

/** What a hold was placed for. Kept after the hold ends, so a repeat is still recognised. */
data class Placement(
    val toolId: ToolId,
    val neighborId: NeighborId,
)

/** A hold that is still standing: waiting in the queue, or set aside for its holder. */
data class Hold(
    val toolId: ToolId,
    val neighborId: NeighborId,
    val setAside: Boolean,
)

data class OpenLoan(
    val toolId: ToolId,
    val borrower: NeighborId,
)

/**
 * Folded from every upstream event the decision reads. Mutable maps, because the shed's history is long and
 * copying the whole state on every event would be wasteful.
 */
@EventSourced(idType = HoldLedger::class)
class HoldState
    @EntityCreator
    constructor() {
        private val tools = mutableSetOf<ToolId>()
        private val retired = mutableSetOf<ToolId>()
        private val registered = mutableSetOf<NeighborId>()
        private val closed = mutableSetOf<NeighborId>()
        private val suspended = mutableSetOf<NeighborId>()
        private val openLoans = mutableMapOf<LoanId, OpenLoan>()
        private val holds = mutableMapOf<HoldId, Hold>()
        private val placements = mutableMapOf<HoldId, Placement>()

        fun placement(holdId: HoldId): Placement? = placements[holdId]

        fun hasTool(toolId: ToolId): Boolean = toolId in tools

        fun isRegistered(neighborId: NeighborId): Boolean = neighborId in registered

        fun isRetired(toolId: ToolId): Boolean = toolId in retired

        fun isClosed(neighborId: NeighborId): Boolean = neighborId in closed

        fun isSuspended(neighborId: NeighborId): Boolean = neighborId in suspended

        fun hasOpenLoan(
            toolId: ToolId,
            neighborId: NeighborId,
        ): Boolean = openLoans.values.any { it.toolId == toolId && it.borrower == neighborId }

        fun hasActiveHold(
            toolId: ToolId,
            neighborId: NeighborId,
        ): Boolean = holds.values.any { it.toolId == toolId && it.neighborId == neighborId }

        /** Checked out, then set aside, then reserved; available only when none of those holds (INV-HOLD-2). */
        fun isAvailable(toolId: ToolId): Boolean =
            openLoans.values.none { it.toolId == toolId } &&
                holds.values.none { it.toolId == toolId }

        @EventSourcingHandler
        fun evolve(event: ToolAdded) {
            tools.add(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolRetired) {
            retired.add(event.toolId)
            endHoldsOn(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            retired.add(event.toolId)
            endLoan(event.loanId)
            endHoldsOn(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolCheckedOut) {
            openLoans[event.loanId] = OpenLoan(event.toolId, event.neighborId)
            // The holder checking the tool out ends their own hold on it, waiting or set aside.
            holds.entries.removeAll { it.value.toolId == event.toolId && it.value.neighborId == event.neighborId }
        }

        @EventSourcingHandler
        fun evolve(event: ToolReturned) {
            endLoan(event.loanId)
        }

        @EventSourcingHandler
        fun evolve(event: NeighborRegistered) {
            registered.add(event.neighborId)
        }

        @EventSourcingHandler
        fun evolve(event: NeighborSuspended) {
            suspended.add(event.neighborId)
        }

        @EventSourcingHandler
        fun evolve(event: NeighborReinstated) {
            suspended.remove(event.neighborId)
        }

        /** Closing ends all of the neighbor's holds. Closed is final, and what shows (N5). */
        @EventSourcingHandler
        fun evolve(event: NeighborAccountClosed) {
            closed.add(event.neighborId)
            holds.entries.removeAll { it.value.neighborId == event.neighborId }
        }

        @EventSourcingHandler
        fun evolve(event: HoldPlaced) {
            placements[event.holdId] = Placement(event.toolId, event.neighborId)
            holds[event.holdId] = Hold(event.toolId, event.neighborId, setAside = false)
        }

        @EventSourcingHandler
        fun evolve(event: HoldFulfilled) {
            holds[event.holdId]?.let { holds[event.holdId] = it.copy(setAside = true) }
        }

        @EventSourcingHandler
        fun evolve(event: HoldCancelled) {
            holds.remove(event.holdId)
        }

        @EventSourcingHandler
        fun evolve(event: HoldExpired) {
            holds.remove(event.holdId)
        }

        private fun endLoan(loanId: LoanId) {
            openLoans.remove(loanId)
        }

        private fun endHoldsOn(toolId: ToolId) {
            holds.entries.removeAll { it.value.toolId == toolId }
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            private fun resolveCriteria(ledger: HoldLedger): EventCriteria =
                EventCriteria
                    .havingAnyTag()
                    .andBeingOneOfTypes(
                        ToolAdded::class.java.name,
                        ToolRetired::class.java.name,
                        ToolLost::class.java.name,
                        ToolCheckedOut::class.java.name,
                        ToolReturned::class.java.name,
                        HoldPlaced::class.java.name,
                        HoldFulfilled::class.java.name,
                        HoldCancelled::class.java.name,
                        HoldExpired::class.java.name,
                        NeighborRegistered::class.java.name,
                        NeighborSuspended::class.java.name,
                        NeighborReinstated::class.java.name,
                        NeighborAccountClosed::class.java.name,
                    )
        }
    }
