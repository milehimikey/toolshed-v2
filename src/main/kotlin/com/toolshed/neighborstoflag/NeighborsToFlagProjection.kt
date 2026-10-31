package com.toolshed.neighborstoflag

import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.flagneighbor.NeighborFlagged
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Applies the events Neighbors To Flag reads. Every handler is idempotent (INV-NTF-8). After each repository write it invokes
 * every [NeighborsToFlagReaction] (Amendment 11), so a reaction always reads a list that has applied the event.
 */
@Component
class NeighborsToFlagProjection(
    private val repository: NeighborsToFlagRepository,
    private val reactions: List<NeighborsToFlagReaction> = emptyList(),
) {
    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.checkOut(event.loanId, event.neighborId)
        logger.debug { "neighbors-to-flag loanId=${event.loanId.value} outcome=checked-out" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: ToolReturned) {
        repository.returned(event.loanId, event.returnedOn, event.late)
        logger.debug { "neighbors-to-flag loanId=${event.loanId.value} outcome=returned late=${event.late}" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: NeighborFlagged) {
        repository.flagged(event.loanIds)
        logger.debug { "neighbors-to-flag neighborId=${event.neighborId.value} outcome=flagged" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: NeighborSuspended) {
        repository.suspend(event.neighborId, event.suspendedAt)
        logger.debug { "neighbors-to-flag neighborId=${event.neighborId.value} outcome=suspended" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: NeighborReinstated) {
        repository.reinstate(event.neighborId, event.reinstatedAt)
        logger.debug { "neighbors-to-flag neighborId=${event.neighborId.value} outcome=reinstated" }
        reactions.forEach { it.listChanged(event) }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.close(event.neighborId)
        logger.debug { "neighbors-to-flag neighborId=${event.neighborId.value} outcome=closed" }
        reactions.forEach { it.listChanged(event) }
    }
}
