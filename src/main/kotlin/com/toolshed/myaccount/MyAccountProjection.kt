package com.toolshed.myaccount

import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

/**
 * Builds My Account from the five account events. Every handler is idempotent (INV-MYAC-10). Closed is final (INV-MYAC-5),
 * and an event that reaches a closed account changes nothing: it can only be a replay.
 */
@Component
class MyAccountProjection(
    private val repository: MyAccountRepository,
) {
    @EventHandler
    fun on(event: NeighborRegistered) {
        // One account per registration: a repeat of the same registration changes nothing.
        repository.register(MyAccountEntry(event.neighborId, event.name, event.email))
    }

    @EventHandler
    fun on(event: NeighborEmailChanged) {
        repository.update(event.neighborId) { if (it.closed) it else it.copy(email = event.email) }
    }

    @EventHandler
    fun on(event: NeighborSuspended) {
        repository.update(event.neighborId) { if (it.closed) it else it.copy(suspended = true) }
    }

    @EventHandler
    fun on(event: NeighborReinstated) {
        repository.update(event.neighborId) { if (it.closed) it else it.copy(suspended = false) }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.update(event.neighborId) { it.copy(closed = true) }
    }
}
