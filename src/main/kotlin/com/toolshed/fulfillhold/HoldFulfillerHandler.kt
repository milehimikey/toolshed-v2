package com.toolshed.fulfillhold

import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.holdstofulfill.HoldsToFulfillReaction
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.returntool.ToolReturned
import org.axonframework.messaging.eventhandling.annotation.EventHandler

/**
 * The Hold Fulfiller (Amendment 7: it acts when its list changes). The list changes only when one of these events
 * lands, so each one wakes the fulfiller to read the list and act on every item on it. Fulfill Hold is safe to
 * repeat, so a wake-up that finds nothing new records nothing. It is registered on Holds To Fulfill's processor after
 * the projection (Amendment 11), so the list has applied the waking event when the fulfiller reads it. It is a @Bean of
 * [HoldFulfillerConfiguration], not a scanned component: Spring registers @Bean methods after every scanned component, and
 * the Axon extension hands a processor its handlers in registration order, so the scanned projection always comes first.
 */
class HoldFulfillerHandler(
    private val fulfiller: HoldFulfiller,
) : HoldsToFulfillReaction {
    @EventHandler
    fun on(event: ToolReturned) = fulfiller.run()

    @EventHandler
    fun on(event: HoldPlaced) = fulfiller.run()

    @EventHandler
    fun on(event: HoldExpired) = fulfiller.run()

    // Hold Fulfilled changes the list (the tool leaves it), so the fulfiller looks again; a no-op on a settled list.
    @EventHandler
    fun on(event: HoldFulfilled) = fulfiller.run()

    @EventHandler
    fun on(event: HoldCancelled) = fulfiller.run()

    @EventHandler
    fun on(event: ToolLost) = fulfiller.run()

    @EventHandler
    fun on(event: NeighborAccountClosed) = fulfiller.run()
}
