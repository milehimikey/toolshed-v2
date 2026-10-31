package com.toolshed.sendpickupnotice

import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.retiretool.ToolRetired
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.messaging.eventstreaming.Tag

/**
 * One hold's pickup-email state, folded from the events the decision reads. A set-aside ends when its hold ends: the holder
 * checks the tool out, the hold is cancelled or expires, the tool is retired, or the holder closes their account (INV-PKN-5).
 */
@EventSourced(idType = PickupNoticeKey::class)
class SendPickupNoticeState
    @EntityCreator
    constructor() {
        private var placed: Placed? = null
        private var collectBy: java.time.LocalDate? = null
        private var ended = false
        private var sent: PickupNoticeSent? = null

        private class Placed(
            val toolId: ToolId,
            val neighborId: NeighborId,
        )

        /** INV-PKN-9: Hold Placed has been recorded for the hold. */
        fun exists(): Boolean = placed != null

        /** The Pickup Notice Sent already recorded for the hold, if any (INV-PKN-2). */
        fun noticeSent(): PickupNoticeSent? = sent

        /** INV-PKN-5: the hold has ended. */
        fun holdEnded(): Boolean = ended

        /** INV-PKN-10: Hold Fulfilled has been recorded for the hold. */
        fun fulfilled(): Boolean = collectBy != null

        /** The collect-by date recorded on Hold Fulfilled (INV-PKN-11), or null while the hold is waiting. */
        fun collectByDate(): java.time.LocalDate? = collectBy

        @EventSourcingHandler
        fun evolve(event: HoldPlaced) {
            if (placed == null) placed = Placed(event.toolId, event.neighborId)
        }

        @EventSourcingHandler
        fun evolve(event: HoldFulfilled) {
            if (collectBy == null) collectBy = event.collectByDate
        }

        @EventSourcingHandler
        fun evolve(event: PickupNoticeSent) {
            if (sent == null) sent = event
        }

        @EventSourcingHandler
        fun evolve(
            @Suppress("UNUSED_PARAMETER") event: HoldCancelled,
        ) {
            ended = true
        }

        @EventSourcingHandler
        fun evolve(
            @Suppress("UNUSED_PARAMETER") event: HoldExpired,
        ) {
            ended = true
        }

        /** The holder's own checkout of the tool collects it, ending the hold. */
        @EventSourcingHandler
        fun evolve(event: ToolCheckedOut) {
            if (placed?.let { it.toolId == event.toolId && it.neighborId == event.neighborId } == true) ended = true
        }

        @EventSourcingHandler
        fun evolve(event: ToolRetired) {
            if (placed?.toolId == event.toolId) ended = true
        }

        @EventSourcingHandler
        fun evolve(event: NeighborAccountClosed) {
            if (placed?.neighborId == event.neighborId) ended = true
        }

        companion object {
            /** The events this decision reads: the hold's own by its tag, and the tool and neighbor events by type. */
            @EventCriteriaBuilder
            @JvmStatic
            fun resolveCriteria(key: PickupNoticeKey): EventCriteria =
                EventCriteria.either(
                    EventCriteria
                        .havingTags(Tag.of("holdId", key.holdId.value.toString()))
                        .andBeingOneOfTypes(
                            HoldPlaced::class.java.name,
                            HoldFulfilled::class.java.name,
                            HoldCancelled::class.java.name,
                            HoldExpired::class.java.name,
                            PickupNoticeSent::class.java.name,
                        ),
                    EventCriteria
                        .havingAnyTag()
                        .andBeingOneOfTypes(
                            ToolCheckedOut::class.java.name,
                            ToolRetired::class.java.name,
                            NeighborAccountClosed::class.java.name,
                        ),
                )
        }
    }
