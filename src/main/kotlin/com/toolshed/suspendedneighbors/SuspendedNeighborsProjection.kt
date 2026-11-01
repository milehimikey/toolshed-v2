package com.toolshed.suspendedneighbors

import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Suspended Neighbors (INV-SPN-1): applies only the events the doc lists, each idempotently (INV-SPN-9). A
 * suspension creates the neighbor's entry; a reinstatement or account closure removes it (INV-SPN-4, 5).
 */
@Component
class SuspendedNeighborsProjection(
    private val repository: SuspendedNeighborsRepository,
) {
    @EventHandler
    fun on(event: NeighborRegistered) {
        repository.saveNeighbor(event.neighborId, event.name, event.email)
        logger.debug { "suspended-neighbors neighborId=${event.neighborId.value} outcome=neighbor-recorded" }
    }

    @EventHandler
    fun on(event: NeighborEmailChanged) {
        repository.changeEmail(event.neighborId, event.email)
        logger.debug { "suspended-neighbors neighborId=${event.neighborId.value} outcome=email-changed" }
    }

    @EventHandler
    fun on(event: NeighborSuspended) {
        repository.suspend(event.neighborId, event.suspendedAt)
        logger.debug { "suspended-neighbors neighborId=${event.neighborId.value} outcome=suspended" }
    }

    @EventHandler
    fun on(event: NeighborReinstated) {
        repository.remove(event.neighborId)
        logger.debug { "suspended-neighbors neighborId=${event.neighborId.value} outcome=removed reason=reinstated" }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.remove(event.neighborId)
        logger.debug { "suspended-neighbors neighborId=${event.neighborId.value} outcome=removed reason=account-closed" }
    }
}
