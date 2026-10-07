package com.toolshed.neighbors

import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

/**
 * Builds the Neighbors view from the five account events. Every handler is idempotent (INV-NBRS-7): applying an event a
 * second time leaves the entry as it was. Closed is final (INV-NBRS-4): nothing after a closure changes the status.
 */
@Component
class NeighborsProjection(
    private val repository: NeighborsRepository,
) {
    @EventHandler
    fun on(event: NeighborRegistered) {
        // One entry per account: a repeat of the same registration changes nothing.
        if (repository.find(event.neighborId) != null) return
        repository.save(NeighborEntry(event.neighborId, event.name, event.email, event.registeredAt))
    }

    @EventHandler
    fun on(event: NeighborEmailChanged) {
        val entry = repository.find(event.neighborId) ?: return
        repository.save(entry.copy(email = event.email))
    }

    @EventHandler
    fun on(event: NeighborSuspended) {
        val entry = repository.find(event.neighborId) ?: return
        if (entry.closed) return
        repository.save(entry.copy(suspended = true))
    }

    @EventHandler
    fun on(event: NeighborReinstated) {
        val entry = repository.find(event.neighborId) ?: return
        if (entry.closed) return
        repository.save(entry.copy(suspended = false))
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        val entry = repository.find(event.neighborId) ?: return
        repository.save(entry.copy(closed = true))
    }
}
