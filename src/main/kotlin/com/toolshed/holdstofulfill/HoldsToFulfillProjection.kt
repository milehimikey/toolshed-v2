package com.toolshed.holdstofulfill

import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Holds To Fulfill: applies only the events the doc lists, each idempotently (INV-HTF-9). The list itself is
 * worked out from current facts when read, never from the order events arrived in (INV-HTF-1). After each repository
 * write it invokes every [HoldsToFulfillReaction] (Amendment 11), so a reaction always reads a list that has applied the event.
 */
@Component
class HoldsToFulfillProjection(
    private val repository: HoldsToFulfillRepository,
    private val reactions: List<HoldsToFulfillReaction> = emptyList(),
) {
    @EventHandler
    fun on(event: HoldPlaced) {
        repository.place(event.holdId, event.toolId, event.neighborId)
        logger.debug { "holds-to-fulfill holdId=${event.holdId.value} outcome=placed" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: HoldFulfilled) {
        repository.fulfill(event.holdId)
        logger.debug { "holds-to-fulfill holdId=${event.holdId.value} outcome=set-aside" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: HoldCancelled) {
        repository.end(event.holdId)
        logger.debug { "holds-to-fulfill holdId=${event.holdId.value} outcome=ended reason=cancelled" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: HoldExpired) {
        repository.end(event.holdId)
        logger.debug { "holds-to-fulfill holdId=${event.holdId.value} outcome=ended reason=expired" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.checkedOut(event.loanId, event.toolId, event.neighborId)
        logger.debug { "holds-to-fulfill toolId=${event.toolId.value} loanId=${event.loanId.value} outcome=loan-opened" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: ToolReturned) {
        repository.loanEnded(event.loanId, event.toolId)
        logger.debug { "holds-to-fulfill toolId=${event.toolId.value} loanId=${event.loanId.value} outcome=loan-ended" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: ToolRetired) {
        repository.retire(event.toolId)
        logger.debug { "holds-to-fulfill toolId=${event.toolId.value} outcome=retired" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: ToolLost) {
        repository.loanEnded(event.loanId, event.toolId)
        repository.endAllOnTool(event.toolId)
        logger.debug { "holds-to-fulfill toolId=${event.toolId.value} outcome=ended reason=lost" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.endAllForNeighbor(event.neighborId)
        logger.debug { "holds-to-fulfill neighborId=${event.neighborId.value} outcome=ended reason=account-closed" }
        reactions.forEach { it.listChanged(event) }
    }
}
