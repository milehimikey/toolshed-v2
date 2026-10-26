package com.toolshed.myholds

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor

/** A neighbor asking to see their own holds. The view is guarded the same way a command is (Amendment 6). */
data class ListMyHolds(
    val neighborId: NeighborId,
)

/** Identity checks that run before the query is asked (Amendment 6): signed in, and asking only for themselves. */
fun Caller.requireLister(query: ListMyHolds) {
    requireNeighbor(
        neighborId = query.neighborId,
        notSignedIn = Refusal("INV-MYHD-19", "Please sign in."),
        mismatch = Refusal("INV-MYHD-20", "You can only see your own holds."),
    )
}
