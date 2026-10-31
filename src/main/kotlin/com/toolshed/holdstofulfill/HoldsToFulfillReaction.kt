package com.toolshed.holdstofulfill

/**
 * A reaction to the Holds To Fulfill list changing (Amendment 11). The projection invokes every reaction at the end of each
 * of its event handlers, after the repository write, so the list has applied the event before the reaction reads it.
 */
interface HoldsToFulfillReaction {
    fun listChanged(event: Any)
}
