package com.toolshed.suspendedneighbors

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import java.time.Instant

/** One row of Suspended Neighbors (the view's field table). */
data class SuspendedNeighborRow(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val suspendedAt: Instant,
)

/** The query's answer: every entry, ordered for the screen (INV-SPN-10). No paging. */
data class SuspendedNeighbors(
    val entries: List<SuspendedNeighborRow>,
)

/** The query the Suspended Neighbors screen asks. Takes nothing. */
object ListSuspendedNeighbors

/** Identity checks that run before the query is asked (Amendment 6, INV-SPN-11): signed in, and the shed manager's role. */
fun Caller.requireSuspendedNeighborsReader() {
    requireShedManager(
        notSignedIn = Refusal("INV-SPN-11", "Please sign in."),
        notShedManager = Refusal("INV-SPN-11", "Only the shed manager can see this screen."),
    )
}

/** An entry as stored, with a creation sequence for the final ordering tie-break. */
data class StoredSuspension(
    val neighborId: NeighborId,
    val suspendedAt: Instant,
    val sequence: Long,
)
