package com.toolshed.cancelhold

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria

/** Where a hold stands. WAITING and SET_ASIDE are live; the other two are final. */
enum class HoldStatus { WAITING, SET_ASIDE, CANCELLED, ENDED }

/** A hold as far as a cancellation cares: which tool, whose, and where it stands. */
data class Hold(
    val holdId: HoldId,
    val toolId: ToolId,
    val holder: NeighborId,
    val status: HoldStatus,
)

/**
 * Folded from every event that starts or ends a hold. Mutable map, because the shed's history is long and
 * copying the whole state on every event would be wasteful.
 */
@EventSourced(idType = HoldLedger::class)
class CancelHoldState
    @EntityCreator
    constructor() {
        private val holds = mutableMapOf<HoldId, Hold>()

        fun hold(holdId: HoldId): Hold? = holds[holdId]

        @EventSourcingHandler
        fun evolve(event: HoldPlaced) {
            holds[event.holdId] = Hold(event.holdId, event.toolId, event.neighborId, HoldStatus.WAITING)
        }

        @EventSourcingHandler
        fun evolve(event: HoldFulfilled) {
            replaceIfLive(event.holdId) { it.copy(status = HoldStatus.SET_ASIDE) }
        }

        @EventSourcingHandler
        fun evolve(event: HoldCancelled) {
            replaceIfLive(event.holdId) { it.copy(status = HoldStatus.CANCELLED) }
        }

        @EventSourcingHandler
        fun evolve(event: HoldExpired) {
            replaceIfLive(event.holdId) { it.copy(status = HoldStatus.ENDED) }
        }

        /** The holder checking the tool out ends their own hold on it. */
        @EventSourcingHandler
        fun evolve(event: ToolCheckedOut) {
            endLive { it.toolId == event.toolId && it.holder == event.neighborId }
        }

        @EventSourcingHandler
        fun evolve(event: ToolRetired) {
            endLive { it.toolId == event.toolId }
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            endLive { it.toolId == event.toolId }
        }

        @EventSourcingHandler
        fun evolve(event: NeighborAccountClosed) {
            endLive { it.holder == event.neighborId }
        }

        private fun replaceIfLive(
            holdId: HoldId,
            change: (Hold) -> Hold,
        ) {
            val hold = holds[holdId] ?: return
            if (hold.status == HoldStatus.WAITING || hold.status == HoldStatus.SET_ASIDE) holds[holdId] = change(hold)
        }

        private fun endLive(matches: (Hold) -> Boolean) {
            holds.replaceAll { _, hold ->
                if ((hold.status == HoldStatus.WAITING || hold.status == HoldStatus.SET_ASIDE) && matches(hold)) {
                    hold.copy(status = HoldStatus.ENDED)
                } else {
                    hold
                }
            }
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            private fun resolveCriteria(ledger: HoldLedger): EventCriteria =
                EventCriteria
                    .havingAnyTag()
                    .andBeingOneOfTypes(
                        HoldPlaced::class.java.name,
                        HoldFulfilled::class.java.name,
                        HoldCancelled::class.java.name,
                        HoldExpired::class.java.name,
                        ToolCheckedOut::class.java.name,
                        ToolRetired::class.java.name,
                        ToolLost::class.java.name,
                        NeighborAccountClosed::class.java.name,
                    )
        }
    }
