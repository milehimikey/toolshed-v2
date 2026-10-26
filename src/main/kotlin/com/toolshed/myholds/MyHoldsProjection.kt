package com.toolshed.myholds

import com.toolshed.addtool.ToolAdded
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordpickupnoticefailure.PickupNoticeFailed
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.sendpickupnotice.PickupNoticeSent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * My Holds (INV-MYHD-11): applies only the events the doc lists. A checkout by anyone other than the hold's own
 * holder leaves every neighbor's My Holds unchanged, so [ToolCheckedOut] only ends the hold matching both its
 * toolId and neighborId, never every hold on the tool.
 */
@Component
class MyHoldsProjection(
    private val repository: MyHoldsRepository,
) {
    @EventHandler
    fun on(event: ToolAdded) {
        repository.saveToolName(event.toolId, event.name)
        logger.debug { "my-holds toolId=${event.toolId.value} outcome=tool-name-recorded" }
    }

    @EventHandler
    fun on(event: HoldPlaced) {
        repository.place(event.holdId, event.toolId, event.neighborId, event.placedAt)
        logger.debug { "my-holds holdId=${event.holdId.value} outcome=placed" }
    }

    @EventHandler
    fun on(event: HoldFulfilled) {
        repository.fulfill(event.holdId, event.collectByDate)
        logger.debug { "my-holds holdId=${event.holdId.value} outcome=fulfilled" }
    }

    @EventHandler
    fun on(event: PickupNoticeSent) {
        repository.noticeSent(event.holdId)
        logger.debug { "my-holds holdId=${event.holdId.value} outcome=notice-sent" }
    }

    @EventHandler
    fun on(event: PickupNoticeFailed) {
        repository.noticeFailed(event.holdId)
        logger.debug { "my-holds holdId=${event.holdId.value} outcome=notice-failed" }
    }

    @EventHandler
    fun on(event: HoldCancelled) {
        repository.end(event.holdId)
        logger.debug { "my-holds holdId=${event.holdId.value} outcome=ended reason=cancelled" }
    }

    @EventHandler
    fun on(event: HoldExpired) {
        repository.end(event.holdId)
        logger.debug { "my-holds holdId=${event.holdId.value} outcome=ended reason=expired" }
    }

    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.endForToolAndNeighbor(event.toolId, event.neighborId)
        logger.debug { "my-holds toolId=${event.toolId.value} neighborId=${event.neighborId.value} outcome=ended reason=checked-out" }
    }

    @EventHandler
    fun on(event: ToolRetired) {
        repository.endAllOnTool(event.toolId)
        logger.debug { "my-holds toolId=${event.toolId.value} outcome=ended reason=retired" }
    }

    @EventHandler
    fun on(event: ToolLost) {
        repository.endAllOnTool(event.toolId)
        logger.debug { "my-holds toolId=${event.toolId.value} outcome=ended reason=lost" }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.endAllForNeighbor(event.neighborId)
        logger.debug { "my-holds neighborId=${event.neighborId.value} outcome=ended reason=account-closed" }
    }
}
