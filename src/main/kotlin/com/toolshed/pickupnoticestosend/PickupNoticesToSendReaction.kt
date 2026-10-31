package com.toolshed.pickupnoticestosend

/**
 * A reaction to the Pickup Notices To Send list changing (Amendment 11). The projection invokes every reaction at the end of
 * each of its event handlers, after the repository write, so the list has applied the event before the reaction reads it.
 */
interface PickupNoticesToSendReaction {
    fun listChanged(event: Any)
}
