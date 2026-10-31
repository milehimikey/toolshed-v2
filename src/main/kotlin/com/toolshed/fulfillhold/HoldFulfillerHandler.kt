package com.toolshed.fulfillhold

import com.toolshed.holdstofulfill.HoldsToFulfillReaction

/**
 * The Hold Fulfiller (Amendment 7: it acts when its list changes). It is a reaction the Holds To Fulfill projection invokes
 * after each list-changing event's repository write (Amendment 11), so it reads a list that has applied that event: one read,
 * then it acts on every item. Fulfill Hold is safe to repeat, so a call that finds nothing new records nothing.
 */
class HoldFulfillerHandler(
    private val fulfiller: HoldFulfiller,
) : HoldsToFulfillReaction {
    override fun listChanged(event: Any) = fulfiller.run()
}
