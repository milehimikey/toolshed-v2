package com.toolshed.checkouttool

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.extendloan.LoanExtended
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
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
import org.axonframework.messaging.eventstreaming.Tag
import java.time.LocalDate

/**
 * What a checkout decision needs to know, evolved from the events it reads. Plain Kotlin
 * types only, so [CheckOutDecider] stays free of framework types.
 */
@EventSourced(idType = CheckoutScope::class)
class CheckOutState
    @EntityCreator
    constructor() {
        private val defaultPeriods = mutableMapOf<ToolId, Int>()
        private val retiredTools = mutableSetOf<ToolId>()
        private val registeredNeighbors = mutableSetOf<NeighborId>()
        private val suspendedNeighbors = mutableSetOf<NeighborId>()
        private val closedNeighbors = mutableSetOf<NeighborId>()
        private val loans = mutableMapOf<LoanId, Loan>()
        private val holds = mutableListOf<Hold>()

        internal class Loan(
            val loanId: LoanId,
            val toolId: ToolId,
            val neighborId: NeighborId,
            val loanPeriodDays: Int,
            var dueDate: LocalDate,
            var open: Boolean,
        )

        /** A hold, in the order it was recorded. [collectByDate] is set once it is fulfilled. */
        internal class Hold(
            val holdId: HoldId,
            val toolId: ToolId,
            val neighborId: NeighborId,
        ) {
            var collectByDate: LocalDate? = null
            var ended: Boolean = false
        }

        internal fun defaultPeriodOf(toolId: ToolId): Int? = defaultPeriods[toolId]

        internal fun hasTool(toolId: ToolId): Boolean = toolId in defaultPeriods

        internal fun isRetired(toolId: ToolId): Boolean = toolId in retiredTools

        internal fun hasNeighbor(neighborId: NeighborId): Boolean = neighborId in registeredNeighbors

        internal fun isClosed(neighborId: NeighborId): Boolean = neighborId in closedNeighbors

        internal fun isSuspended(neighborId: NeighborId): Boolean = neighborId in suspendedNeighbors

        internal fun loan(loanId: LoanId): Loan? = loans[loanId]

        internal fun openLoansOf(toolId: ToolId): List<Loan> = loans.values.filter { it.toolId == toolId && it.open }

        internal fun openLoansOf(neighborId: NeighborId): List<Loan> = loans.values.filter { it.neighborId == neighborId && it.open }

        /** Holds still in the queue: placed, not fulfilled, not ended. Recorded order. */
        internal fun waitingHolds(toolId: ToolId): List<Hold> =
            holds.filter { it.toolId == toolId && it.collectByDate == null && !it.ended }

        /** Holds fulfilled (set aside) and not yet ended. */
        internal fun setAsideHolds(toolId: ToolId): List<Hold> =
            holds.filter { it.toolId == toolId && it.collectByDate != null && !it.ended }

        @EventSourcingHandler
        fun evolve(event: ToolAdded) {
            defaultPeriods[event.toolId] = event.defaultCheckoutPeriodDays
        }

        @EventSourcingHandler
        fun evolve(event: ToolRetired) {
            retiredTools += event.toolId
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            retiredTools += event.toolId
            loans[event.loanId]?.open = false
        }

        @EventSourcingHandler
        fun evolve(event: ToolReturned) {
            loans[event.loanId]?.open = false
        }

        @EventSourcingHandler
        fun evolve(event: LoanExtended) {
            loans[event.loanId]?.dueDate = event.dueDate
        }

        @EventSourcingHandler
        fun evolve(event: com.toolshed.checkouttool.ToolCheckedOut) {
            loans[event.loanId] =
                Loan(event.loanId, event.toolId, event.neighborId, event.loanPeriodDays, event.dueDate, open = true)
            // A holder who checks out the tool they hold ends that hold (INV-CHK-9).
            holds
                .filter { it.toolId == event.toolId && it.neighborId == event.neighborId && !it.ended }
                .forEach { it.ended = true }
        }

        @EventSourcingHandler
        fun evolve(event: NeighborRegistered) {
            registeredNeighbors += event.neighborId
        }

        @EventSourcingHandler
        fun evolve(event: NeighborSuspended) {
            suspendedNeighbors += event.neighborId
        }

        @EventSourcingHandler
        fun evolve(event: NeighborReinstated) {
            suspendedNeighbors -= event.neighborId
        }

        @EventSourcingHandler
        fun evolve(event: NeighborAccountClosed) {
            closedNeighbors += event.neighborId
            // Closing ends every hold the neighbor has, so holds behind theirs move up (close-account.md).
            holds
                .filter { it.neighborId == event.neighborId && !it.ended }
                .forEach { it.ended = true }
        }

        @EventSourcingHandler
        fun evolve(event: HoldPlaced) {
            holds += Hold(event.holdId, event.toolId, event.neighborId)
        }

        @EventSourcingHandler
        fun evolve(event: HoldFulfilled) {
            holds.firstOrNull { it.holdId == event.holdId }?.collectByDate = event.collectByDate
        }

        @EventSourcingHandler
        fun evolve(event: HoldCancelled) {
            holds.firstOrNull { it.holdId == event.holdId }?.ended = true
        }

        @EventSourcingHandler
        fun evolve(event: HoldExpired) {
            holds.firstOrNull { it.holdId == event.holdId }?.ended = true
        }

        companion object {
            /**
             * Everything a checkout decision reads, for one scope. Tags are matched per the DCB tag
             * convention; the overdue and hold-ending facts are found by type alone, because their
             * events don't carry the borrower or tool that the decision needs to look them up by.
             */
            @EventCriteriaBuilder
            @JvmStatic
            fun resolveCriteria(scope: CheckoutScope): EventCriteria =
                EventCriteria.either(
                    EventCriteria
                        .havingTags(Tag.of("loanId", scope.loanId.value.toString()))
                        .andBeingOneOfTypes(
                            ToolCheckedOut::class.java.name,
                            ToolReturned::class.java.name,
                            ToolLost::class.java.name,
                            LoanExtended::class.java.name,
                        ),
                    EventCriteria
                        .havingTags(Tag.of("toolId", scope.toolId.value.toString()))
                        .andBeingOneOfTypes(
                            ToolAdded::class.java.name,
                            ToolRetired::class.java.name,
                            ToolLost::class.java.name,
                            ToolReturned::class.java.name,
                            ToolCheckedOut::class.java.name,
                            HoldPlaced::class.java.name,
                            HoldFulfilled::class.java.name,
                        ),
                    EventCriteria
                        .havingTags(Tag.of("neighborId", scope.neighborId.value.toString()))
                        .andBeingOneOfTypes(
                            NeighborRegistered::class.java.name,
                            NeighborSuspended::class.java.name,
                            NeighborReinstated::class.java.name,
                            NeighborAccountClosed::class.java.name,
                            ToolCheckedOut::class.java.name,
                            HoldPlaced::class.java.name,
                            HoldFulfilled::class.java.name,
                        ),
                    EventCriteria
                        .havingAnyTag()
                        .andBeingOneOfTypes(
                            ToolReturned::class.java.name,
                            ToolLost::class.java.name,
                            LoanExtended::class.java.name,
                            HoldCancelled::class.java.name,
                            HoldExpired::class.java.name,
                            // By type, not by tag: a holder's hold ends when their account closes, whoever is asking.
                            NeighborAccountClosed::class.java.name,
                        ),
                )
        }
    }
