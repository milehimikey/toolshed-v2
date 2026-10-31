package com.toolshed.neighborstoflag

import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.flagneighbor.NeighborFlagged
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

/** Applies the events Neighbors To Flag reads. Every handler is idempotent (INV-NTF-8). */
@Component
class NeighborsToFlagProjection(
    private val repository: NeighborsToFlagRepository,
) {
    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.checkOut(event.loanId, event.neighborId)
    }

    @EventHandler
    fun on(event: ToolReturned) {
        repository.returned(event.loanId, event.returnedOn, event.late)
    }

    @EventHandler
    fun on(event: NeighborFlagged) {
        repository.flagged(event.loanIds)
    }

    @EventHandler
    fun on(event: NeighborSuspended) {
        repository.suspend(event.neighborId)
    }

    @EventHandler
    fun on(event: NeighborReinstated) {
        repository.reinstate(event.neighborId)
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.close(event.neighborId)
    }
}
