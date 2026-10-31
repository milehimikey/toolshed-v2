package com.toolshed.fulfillhold

import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.returntool.ToolReturned
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

/**
 * The Hold Fulfiller (Amendment 7: it acts when its list changes). The list changes only when one of these events
 * lands, so each one wakes the fulfiller to read the list and act on every item on it. Fulfill Hold is safe to
 * repeat, so a wake-up that finds nothing new, or an item the list has not caught up on, records nothing.
 */
@Component
class HoldFulfillerHandler(
    private val fulfiller: HoldFulfiller,
) {
    @EventHandler
    fun on(event: ToolReturned) = fulfiller.run()

    @EventHandler
    fun on(event: HoldPlaced) = fulfiller.run()

    @EventHandler
    fun on(event: HoldExpired) = fulfiller.run()

    // The list's own Hold Fulfilled may land after a return it was racing; this gives a second look at the list.
    @EventHandler
    fun on(event: HoldFulfilled) = fulfiller.run()

    @EventHandler
    fun on(event: HoldCancelled) = fulfiller.run()

    @EventHandler
    fun on(event: ToolLost) = fulfiller.run()

    @EventHandler
    fun on(event: NeighborAccountClosed) = fulfiller.run()
}
