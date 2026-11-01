package com.toolshed.myaccount

import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/** Applies the five events My Account reads (INV-MYAC-7). Every handler is idempotent (INV-MYAC-10). */
@Component
class MyAccountProjection(
    private val repository: MyAccountRepository,
) {
    @EventHandler
    fun on(event: NeighborRegistered) {
        repository.register(event.neighborId, event.name, event.email)
        logger.debug { "my-account neighborId=${event.neighborId.value} outcome=registered" }
    }

    @EventHandler
    fun on(event: NeighborEmailChanged) {
        repository.changeEmail(event.neighborId, event.email)
        logger.debug { "my-account neighborId=${event.neighborId.value} outcome=email-changed" }
    }

    @EventHandler
    fun on(event: NeighborSuspended) {
        repository.suspend(event.neighborId)
        logger.debug { "my-account neighborId=${event.neighborId.value} outcome=suspended" }
    }

    @EventHandler
    fun on(event: NeighborReinstated) {
        repository.reinstate(event.neighborId)
        logger.debug { "my-account neighborId=${event.neighborId.value} outcome=reinstated" }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.close(event.neighborId)
        logger.debug { "my-account neighborId=${event.neighborId.value} outcome=closed" }
    }
}
