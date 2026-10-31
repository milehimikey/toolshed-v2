package com.toolshed.neighborstoflag

/**
 * A reaction to the Neighbors To Flag list changing (Amendment 11). The projection invokes every reaction at the end of each
 * of its event handlers, after the repository write, so the list has applied the event before the reaction reads it.
 */
interface NeighborsToFlagReaction {
    fun listChanged(event: Any)
}
