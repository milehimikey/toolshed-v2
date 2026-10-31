package com.toolshed.flagneighbor

import com.toolshed.axon.LoanId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.messaging.eventstreaming.Tag
import java.time.LocalDate
import java.util.UUID

/** One late return that may still count toward a flag: the loan and the day it was returned. */
data class LateReturn(
    val loanId: LoanId,
    val returnedOn: LocalDate,
)

/** What the Flag Neighbor decision needs to know about one neighbor, worked out from their events. */
data class FlagFacts(
    val registered: Boolean,
    val suspended: Boolean,
    val closed: Boolean,
    /** Late returns not used up by a flag, returned after the latest reinstatement and not while suspended. Window not applied. */
    val lateReturns: List<LateReturn>,
    /** The loan set each earlier Neighbor Flagged named. */
    val flaggedSets: List<Set<LoanId>>,
)

/**
 * The neighbor's flag-relevant history. Tool Returned carries no neighbor, so the criteria also reads every return in
 * the shed, and a return counts only when its loan was checked out by this neighbor (their Tool Checked Out is read
 * through the neighborId tag). That is the same wider boundary Close Account accepts.
 */
@EventSourcedEntity(tagKey = "neighborId")
class FlagState
    @EntityCreator
    constructor() {
        private var registered = false
        private var suspended = false
        private var closed = false
        private val loans = mutableSetOf<LoanId>()
        private val usedUp = mutableSetOf<LoanId>()
        private val lateReturns = linkedMapOf<LoanId, LateReturn>()
        private val flaggedSets = mutableListOf<Set<LoanId>>()

        val facts: FlagFacts
            get() = FlagFacts(registered, suspended, closed, lateReturns.values.toList(), flaggedSets.toList())

        @EventSourcingHandler
        fun on(event: NeighborRegistered) {
            registered = true
        }

        @EventSourcingHandler
        fun on(event: ToolCheckedOut) {
            loans.add(event.loanId)
        }

        @EventSourcingHandler
        fun on(event: ToolReturned) {
            if (event.late && !suspended && event.loanId in loans && event.loanId !in usedUp) {
                lateReturns[event.loanId] = LateReturn(event.loanId, event.returnedOn)
            }
        }

        @EventSourcingHandler
        fun on(event: NeighborFlagged) {
            usedUp.addAll(event.loanIds)
            lateReturns.keys.removeAll(event.loanIds.toSet())
            flaggedSets.add(event.loanIds.toSet())
        }

        @EventSourcingHandler
        fun on(event: NeighborSuspended) {
            suspended = true
        }

        @EventSourcingHandler
        fun on(event: NeighborReinstated) {
            suspended = false
            lateReturns.clear()
        }

        @EventSourcingHandler
        fun on(event: NeighborAccountClosed) {
            closed = true
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            fun resolveCriteria(neighborId: UUID): EventCriteria =
                EventCriteria.either(
                    EventCriteria.havingTags(Tag.of("neighborId", neighborId.toString())),
                    EventCriteria.havingAnyTag().andBeingOneOfTypes(ToolReturned::class.java.name),
                )
        }
    }
