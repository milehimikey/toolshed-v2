package com.toolshed.extendloan

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
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria
import java.time.LocalDate

/**
 * The shed's loans, holds and suspensions, folded from the events the extension decision reads.
 */
@EventSourced(idType = LoanLedger::class)
class LoanLedgerState
    @EntityCreator
    constructor() {
        private val loans = mutableMapOf<LoanId, Loan>()
        private val defaultPeriods = mutableMapOf<ToolId, Int>()
        private val retiredTools = mutableSetOf<ToolId>()
        private val suspended = mutableSetOf<NeighborId>()
        private val waitingHolds = mutableMapOf<HoldId, WaitingHold>()

        fun loan(loanId: LoanId): Loan? = loans[loanId]

        fun defaultPeriodDays(toolId: ToolId): Int? = defaultPeriods[toolId]

        fun isRetired(toolId: ToolId): Boolean = toolId in retiredTools

        fun isSuspended(neighborId: NeighborId): Boolean = neighborId in suspended

        /** A hold is waiting from Hold Placed until it is fulfilled, cancelled, expired or otherwise ends. */
        fun waitingHoldsOn(toolId: ToolId): Int = waitingHolds.values.count { it.toolId == toolId }

        @EventSourcingHandler
        fun evolve(event: ToolAdded) {
            defaultPeriods[event.toolId] = event.defaultCheckoutPeriodDays
        }

        @EventSourcingHandler
        fun evolve(event: ToolRetired) {
            retiredTools.add(event.toolId)
            endHoldsOn(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolCheckedOut) {
            loans[event.loanId] = Loan(event.loanId, event.toolId, event.neighborId, event.dueDate, emptySet(), false)
            // The holder checking the tool out ends their own waiting holds on it.
            waitingHolds.entries.removeAll { it.value.toolId == event.toolId && it.value.neighborId == event.neighborId }
        }

        @EventSourcingHandler
        fun evolve(event: LoanExtended) {
            val loan = loans[event.loanId] ?: return
            loans[event.loanId] = loan.copy(dueDate = event.dueDate, extendedFrom = loan.extendedFrom + event.fromDueDate)
        }

        @EventSourcingHandler
        fun evolve(event: ToolReturned) {
            endLoan(event.loanId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            endLoan(event.loanId)
            endHoldsOn(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: NeighborSuspended) {
            suspended.add(event.neighborId)
        }

        @EventSourcingHandler
        fun evolve(event: NeighborReinstated) {
            suspended.remove(event.neighborId)
        }

        @EventSourcingHandler
        fun evolve(event: HoldPlaced) {
            waitingHolds[event.holdId] = WaitingHold(event.toolId, event.neighborId)
        }

        @EventSourcingHandler
        fun evolve(event: HoldFulfilled) {
            waitingHolds.remove(event.holdId)
        }

        @EventSourcingHandler
        fun evolve(event: HoldCancelled) {
            waitingHolds.remove(event.holdId)
        }

        @EventSourcingHandler
        fun evolve(event: HoldExpired) {
            waitingHolds.remove(event.holdId)
        }

        /** Closing an account ends that neighbor's waiting holds (the holder closes their account). */
        @EventSourcingHandler
        fun evolve(event: NeighborAccountClosed) {
            waitingHolds.entries.removeAll { it.value.neighborId == event.neighborId }
        }

        private fun endLoan(loanId: LoanId) {
            loans[loanId] = loans[loanId]?.copy(ended = true) ?: return
        }

        private fun endHoldsOn(toolId: ToolId) {
            waitingHolds.entries.removeAll { it.value.toolId == toolId }
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            private fun resolveCriteria(ledger: LoanLedger): EventCriteria =
                EventCriteria
                    .havingAnyTag()
                    .andBeingOneOfTypes(
                        ToolAdded::class.java.name,
                        ToolRetired::class.java.name,
                        ToolCheckedOut::class.java.name,
                        LoanExtended::class.java.name,
                        ToolReturned::class.java.name,
                        ToolLost::class.java.name,
                        NeighborSuspended::class.java.name,
                        NeighborReinstated::class.java.name,
                        HoldPlaced::class.java.name,
                        HoldFulfilled::class.java.name,
                        HoldCancelled::class.java.name,
                        HoldExpired::class.java.name,
                        NeighborAccountClosed::class.java.name,
                    )
        }
    }

/** A loan as far as an extension cares: whose it is, what it's for, and when it's due. */
data class Loan(
    val loanId: LoanId,
    val toolId: ToolId,
    val borrower: NeighborId,
    val dueDate: LocalDate,
    val extendedFrom: Set<LocalDate>,
    val ended: Boolean,
)

data class WaitingHold(
    val toolId: ToolId,
    val neighborId: NeighborId,
)
