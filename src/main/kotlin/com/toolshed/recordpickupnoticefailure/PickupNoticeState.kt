package com.toolshed.recordpickupnoticefailure

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.sendpickupnotice.PickupNoticeSent
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.messaging.eventstreaming.Tag

/**
 * One pickup email's state, folded from the events the failure decision reads. Whether the set-aside has ended
 * follows the rule in the Decisions log: the holder collects, the hold is cancelled or expires, the tool is retired
 * or lost, or the holder closes their account (a lost tool is retired).
 */
@EventSourced(idType = PickupNoticeKey::class)
class PickupNoticeState
    @EntityCreator
    constructor() {
        private val sent = mutableSetOf<HoldId>()
        private val failures = mutableMapOf<HoldId, String>()
        private val holds = mutableMapOf<HoldId, SetAside>()

        private class SetAside(
            val toolId: ToolId,
            val neighborId: NeighborId,
        ) {
            var ended: Boolean = false
        }

        fun noticeSent(holdId: HoldId): Boolean = holdId in sent

        fun failureReason(holdId: HoldId): String? = failures[holdId]

        /** A hold whose set-aside we never saw is treated as ended: nothing may be recorded against it. */
        fun holdEnded(holdId: HoldId): Boolean = holds[holdId]?.ended ?: true

        @EventSourcingHandler
        fun evolve(event: PickupNoticeSent) {
            sent += event.holdId
        }

        @EventSourcingHandler
        fun evolve(event: PickupNoticeFailed) {
            failures[event.holdId] = event.reason
        }

        @EventSourcingHandler
        fun evolve(event: HoldFulfilled) {
            holds[event.holdId] = SetAside(event.toolId, event.neighborId)
        }

        @EventSourcingHandler
        fun evolve(event: HoldCancelled) {
            holds[event.holdId]?.ended = true
        }

        @EventSourcingHandler
        fun evolve(event: HoldExpired) {
            holds[event.holdId]?.ended = true
        }

        /** The holder's own checkout of the tool collects it, ending the set-aside. */
        @EventSourcingHandler
        fun evolve(event: ToolCheckedOut) {
            holds.values
                .filter { it.toolId == event.toolId && it.neighborId == event.neighborId && !it.ended }
                .forEach { it.ended = true }
        }

        @EventSourcingHandler
        fun evolve(event: ToolRetired) {
            endSetAsidesOf(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            endSetAsidesOf(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: NeighborAccountClosed) {
            holds.values.filter { it.neighborId == event.neighborId }.forEach { it.ended = true }
        }

        private fun endSetAsidesOf(toolId: ToolId) {
            holds.values.filter { it.toolId == toolId }.forEach { it.ended = true }
        }

        companion object {
            /** The events this decision reads: the hold's own by its tag, and the tool and borrower events by type. */
            @EventCriteriaBuilder
            @JvmStatic
            fun resolveCriteria(key: PickupNoticeKey): EventCriteria =
                EventCriteria.either(
                    EventCriteria
                        .havingTags(Tag.of("holdId", key.holdId.value.toString()))
                        .andBeingOneOfTypes(
                            PickupNoticeSent::class.java.name,
                            PickupNoticeFailed::class.java.name,
                            HoldFulfilled::class.java.name,
                            HoldCancelled::class.java.name,
                            HoldExpired::class.java.name,
                        ),
                    EventCriteria
                        .havingAnyTag()
                        .andBeingOneOfTypes(
                            ToolCheckedOut::class.java.name,
                            ToolRetired::class.java.name,
                            ToolLost::class.java.name,
                            NeighborAccountClosed::class.java.name,
                        ),
                )
        }
    }
