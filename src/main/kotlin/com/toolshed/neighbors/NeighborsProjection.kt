package com.toolshed.neighbors

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
 * Neighbors (INV-NBRS-1): one entry per Neighbor Registered and nothing else creates one. Applies only the five
 * events the doc lists, each idempotently (INV-NBRS-7). Closed accounts stay listed (INV-NBRS-3).
 */
@Component
class NeighborsProjection(
    private val repository: NeighborsRepository,
) {
    @EventHandler
    fun on(event: NeighborRegistered) {
        repository.register(event.neighborId, event.name, event.email, event.registeredAt)
        logger.debug { "neighbors neighborId=${event.neighborId.value} outcome=registered" }
    }

    @EventHandler
    fun on(event: NeighborEmailChanged) {
        repository.changeEmail(event.neighborId, event.email, event.changedAt)
        logger.debug { "neighbors neighborId=${event.neighborId.value} outcome=email-changed" }
    }

    @EventHandler
    fun on(event: NeighborSuspended) {
        repository.suspend(event.neighborId, event.suspendedAt)
        logger.debug { "neighbors neighborId=${event.neighborId.value} outcome=suspended" }
    }

    @EventHandler
    fun on(event: NeighborReinstated) {
        repository.reinstate(event.neighborId, event.reinstatedAt)
        logger.debug { "neighbors neighborId=${event.neighborId.value} outcome=reinstated" }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.close(event.neighborId)
        logger.debug { "neighbors neighborId=${event.neighborId.value} outcome=closed" }
    }
}
