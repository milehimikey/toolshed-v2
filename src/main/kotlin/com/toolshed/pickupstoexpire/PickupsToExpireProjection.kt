package com.toolshed.pickupstoexpire

import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.retiretool.ToolRetired
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Pickups To Expire: one row per Hold Fulfilled (INV-PTE-1), removed by the events the doc lists, each applied
 * idempotently (INV-PTE-10). Whether a row is due is never stored; the query works it out from today.
 * Tool Lost needs no handler: a lost tool is always on loan, so never set aside.
 */
@Component
class PickupsToExpireProjection(
    private val repository: PickupsToExpireRepository,
) {
    @EventHandler
    fun on(event: HoldFulfilled) {
        repository.setAside(event.holdId, event.toolId, event.neighborId, event.collectByDate)
        logger.debug { "pickups-to-expire holdId=${event.holdId.value} outcome=set-aside" }
    }

    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.checkedOut(event.toolId, event.neighborId)
        logger.debug { "pickups-to-expire toolId=${event.toolId.value} outcome=holder-checked-out" }
    }

    @EventHandler
    fun on(event: HoldCancelled) {
        repository.end(event.holdId)
        logger.debug { "pickups-to-expire holdId=${event.holdId.value} outcome=ended reason=cancelled" }
    }

    @EventHandler
    fun on(event: HoldExpired) {
        repository.end(event.holdId)
        logger.debug { "pickups-to-expire holdId=${event.holdId.value} outcome=ended reason=expired" }
    }

    @EventHandler
    fun on(event: ToolRetired) {
        repository.retire(event.toolId)
        logger.debug { "pickups-to-expire toolId=${event.toolId.value} outcome=ended reason=retired" }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.endAllForNeighbor(event.neighborId)
        logger.debug { "pickups-to-expire neighborId=${event.neighborId.value} outcome=ended reason=account-closed" }
    }
}
