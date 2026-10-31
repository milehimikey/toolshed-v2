package com.toolshed.expirehold

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.retiretool.ToolRetired
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria
import java.time.LocalDate

/** A hold as far as an expiry cares. [collectByDate] is as Hold Fulfilled recorded it, null until then. */
class ExpireHoldView(
    val holdId: HoldId,
    val toolId: ToolId,
    val holder: NeighborId,
) {
    var collectByDate: LocalDate? = null
    var ended = false
    var expired = false
}

/** Folded from every event that starts or ends a hold. The collect-by date is read as recorded, never worked out. */
@EventSourced(idType = ExpireLedger::class)
class ExpireState
    @EntityCreator
    constructor() {
        private val holds = HashMap<HoldId, ExpireHoldView>()

        fun hold(holdId: HoldId): ExpireHoldView? = holds[holdId]

        @EventSourcingHandler
        fun evolve(event: HoldPlaced) {
            holds.putIfAbsent(event.holdId, ExpireHoldView(event.holdId, event.toolId, event.neighborId))
        }

        @EventSourcingHandler
        fun evolve(event: HoldFulfilled) {
            val hold = holds[event.holdId] ?: return
            if (hold.collectByDate == null) hold.collectByDate = event.collectByDate
        }

        @EventSourcingHandler
        fun evolve(event: HoldCancelled) {
            holds[event.holdId]?.ended = true
        }

        @EventSourcingHandler
        fun evolve(event: HoldExpired) {
            holds[event.holdId]?.let {
                it.ended = true
                it.expired = true
            }
        }

        /** Only the holder's own checkout ends their hold (INV-EXP-3); someone else's leaves it to expire. */
        @EventSourcingHandler
        fun evolve(event: ToolCheckedOut) {
            holds.values.filter { it.toolId == event.toolId && it.holder == event.neighborId }.forEach { it.ended = true }
        }

        @EventSourcingHandler
        fun evolve(event: ToolRetired) {
            holds.values.filter { it.toolId == event.toolId }.forEach { it.ended = true }
        }

        @EventSourcingHandler
        fun evolve(event: NeighborAccountClosed) {
            holds.values.filter { it.holder == event.neighborId }.forEach { it.ended = true }
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            private fun resolveCriteria(ledger: ExpireLedger): EventCriteria =
                EventCriteria
                    .havingAnyTag()
                    .andBeingOneOfTypes(
                        HoldPlaced::class.java.name,
                        HoldFulfilled::class.java.name,
                        HoldCancelled::class.java.name,
                        HoldExpired::class.java.name,
                        ToolCheckedOut::class.java.name,
                        ToolRetired::class.java.name,
                        NeighborAccountClosed::class.java.name,
                    )
        }
    }
