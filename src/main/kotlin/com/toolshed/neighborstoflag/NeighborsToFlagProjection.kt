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

/** Applies the events Neighbors To Flag reads. Every handler is idempotent (INV-NTF-8). */
@Component
class NeighborsToFlagProjection(
    private val repository: NeighborsToFlagRepository,
) {
    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.checkOut(event.loanId, event.neighborId)
        logger.debug { "neighbors-to-flag loanId=${event.loanId.value} outcome=checked-out" }
    }

    @EventHandler
    fun on(event: ToolReturned) {
        repository.returned(event.loanId, event.returnedOn, event.late)
        logger.debug { "neighbors-to-flag loanId=${event.loanId.value} outcome=returned late=${event.late}" }
    }

    @EventHandler
    fun on(event: NeighborFlagged) {
        repository.flagged(event.loanIds)
        logger.debug { "neighbors-to-flag neighborId=${event.neighborId.value} outcome=flagged" }
    }

    @EventHandler
    fun on(event: NeighborSuspended) {
        repository.suspend(event.neighborId, event.suspendedAt)
        logger.debug { "neighbors-to-flag neighborId=${event.neighborId.value} outcome=suspended" }
    }

    @EventHandler
    fun on(event: NeighborReinstated) {
        repository.reinstate(event.neighborId, event.reinstatedAt)
        logger.debug { "neighbors-to-flag neighborId=${event.neighborId.value} outcome=reinstated" }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.close(event.neighborId)
        logger.debug { "neighbors-to-flag neighborId=${event.neighborId.value} outcome=closed" }
    }
}
