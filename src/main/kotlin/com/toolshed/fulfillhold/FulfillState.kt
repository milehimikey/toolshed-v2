package com.toolshed.fulfillhold

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria

/** A hold as far as a fulfilment cares. [ended] is final; [setAside] is fulfilled and not yet ended. */
class FulfillHoldView(
    val holdId: HoldId,
    val toolId: ToolId,
    val holder: NeighborId,
) {
    var everFulfilled = false
    var ended = false

    val setAside: Boolean get() = everFulfilled && !ended
    val waiting: Boolean get() = !everFulfilled && !ended
}

/**
 * Folded from every event that starts or ends a hold or a loan. Hold order is the order Hold Placed was recorded
 * (INV-FUL-2), never a comparison of times.
 */
@EventSourced(idType = FulfillLedger::class)
class FulfillState
    @EntityCreator
    constructor() {
        private val holds = LinkedHashMap<HoldId, FulfillHoldView>()
        private val openLoans = mutableMapOf<ToolId, MutableSet<LoanId>>()
        private val retired = mutableSetOf<ToolId>()

        fun hold(holdId: HoldId): FulfillHoldView? = holds[holdId]

        /** INV-FUL-3: a loan of the tool is open. */
        fun isOnLoan(toolId: ToolId): Boolean = openLoans[toolId].orEmpty().isNotEmpty()

        /** INV-FUL-4: some hold on the tool is fulfilled and not yet ended. */
        fun isSetAside(toolId: ToolId): Boolean = holds.values.any { it.toolId == toolId && it.setAside }

        /** INV-FUL-2: the waiting hold whose Hold Placed was recorded first. */
        fun frontHold(toolId: ToolId): HoldId? = holds.values.firstOrNull { it.toolId == toolId && it.waiting }?.holdId

        @EventSourcingHandler
        fun evolve(event: HoldPlaced) {
            holds.putIfAbsent(event.holdId, FulfillHoldView(event.holdId, event.toolId, event.neighborId))
        }

        @EventSourcingHandler
        fun evolve(event: HoldFulfilled) {
            holds[event.holdId]?.everFulfilled = true
        }

        @EventSourcingHandler
        fun evolve(event: HoldCancelled) {
            holds[event.holdId]?.ended = true
        }

        @EventSourcingHandler
        fun evolve(event: HoldExpired) {
            holds[event.holdId]?.ended = true
        }

        /** The holder checking the tool out ends their own hold on it, and opens the loan. */
        @EventSourcingHandler
        fun evolve(event: ToolCheckedOut) {
            openLoans.getOrPut(event.toolId) { mutableSetOf() }.add(event.loanId)
            holds.values.filter { it.toolId == event.toolId && it.holder == event.neighborId }.forEach { it.ended = true }
        }

        @EventSourcingHandler
        fun evolve(event: ToolReturned) {
            openLoans[event.toolId]?.remove(event.loanId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            openLoans[event.toolId]?.remove(event.loanId)
            holds.values.filter { it.toolId == event.toolId }.forEach { it.ended = true }
        }

        @EventSourcingHandler
        fun evolve(event: ToolRetired) {
            retired.add(event.toolId)
            holds.values.filter { it.toolId == event.toolId }.forEach { it.ended = true }
        }

        @EventSourcingHandler
        fun evolve(event: NeighborAccountClosed) {
            holds.values.filter { it.holder == event.neighborId }.forEach { it.ended = true }
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            private fun resolveCriteria(ledger: FulfillLedger): EventCriteria =
                EventCriteria
                    .havingAnyTag()
                    .andBeingOneOfTypes(
                        HoldPlaced::class.java.name,
                        HoldFulfilled::class.java.name,
                        HoldCancelled::class.java.name,
                        HoldExpired::class.java.name,
                        ToolCheckedOut::class.java.name,
                        ToolReturned::class.java.name,
                        ToolLost::class.java.name,
                        ToolRetired::class.java.name,
                        NeighborAccountClosed::class.java.name,
                    )
        }
    }
