package com.toolshed.pickupnoticestosend

import com.toolshed.addtool.ToolAdded
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.sendpickupnotice.PickupNoticeSent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Pickup Notices To Send: applies only the events the doc lists, each idempotently (INV-PNS-7). After each repository
 * write it invokes every [PickupNoticesToSendReaction] (Amendment 11), so a reaction reads a list that has applied the event.
 */
@Component
class PickupNoticesToSendProjection(
    private val repository: PickupNoticesToSendRepository,
    private val reactions: List<PickupNoticesToSendReaction> = emptyList(),
) {
    @EventHandler
    fun on(event: NeighborRegistered) {
        repository.neighborRegistered(event.neighborId, event.name, event.email)
        logger.debug { "pickup-notices-to-send neighborId=${event.neighborId.value} outcome=neighbor-known" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: NeighborEmailChanged) {
        repository.emailChanged(event.neighborId, event.email)
        logger.debug { "pickup-notices-to-send neighborId=${event.neighborId.value} outcome=email-changed" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: ToolAdded) {
        repository.toolAdded(event.toolId, event.name)
        logger.debug { "pickup-notices-to-send toolId=${event.toolId.value} outcome=tool-known" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: HoldFulfilled) {
        repository.fulfilled(event.holdId, event.toolId, event.neighborId, event.collectByDate)
        logger.debug { "pickup-notices-to-send holdId=${event.holdId.value} outcome=set-aside" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: PickupNoticeSent) {
        repository.end(event.holdId)
        logger.debug { "pickup-notices-to-send holdId=${event.holdId.value} outcome=left reason=notice-sent" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: HoldCancelled) {
        repository.end(event.holdId)
        logger.debug { "pickup-notices-to-send holdId=${event.holdId.value} outcome=left reason=cancelled" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: HoldExpired) {
        repository.end(event.holdId)
        logger.debug { "pickup-notices-to-send holdId=${event.holdId.value} outcome=left reason=expired" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.checkedOut(event.toolId, event.neighborId)
        logger.debug { "pickup-notices-to-send toolId=${event.toolId.value} outcome=checked-out" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: ToolRetired) {
        repository.retire(event.toolId)
        logger.debug { "pickup-notices-to-send toolId=${event.toolId.value} outcome=retired" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.accountClosed(event.neighborId)
        logger.debug { "pickup-notices-to-send neighborId=${event.neighborId.value} outcome=account-closed" }
        reactions.forEach { it.listChanged(event) }
    }
}
