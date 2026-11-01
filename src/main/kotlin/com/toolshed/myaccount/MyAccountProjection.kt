package com.toolshed.myaccount

import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

/** Applies the five events My Account reads (INV-MYAC-7). Every handler is idempotent (INV-MYAC-10). */
@Component
class MyAccountProjection(
    private val repository: MyAccountRepository,
) {
    @EventHandler
    fun on(event: NeighborRegistered) {
        repository.register(event.neighborId, event.name, event.email)
    }

    @EventHandler
    fun on(event: NeighborEmailChanged) {
        repository.changeEmail(event.neighborId, event.email)
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
