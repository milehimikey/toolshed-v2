package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import java.time.Instant

/** One row of Neighbors (the view's field table). */
data class NeighborRow(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val registeredAt: Instant,
    val status: String,
    val canSuspend: Boolean,
)

/** The query's answer: every account, ordered for the screen (INV-NBRS-8). No paging. */
data class Neighbors(
    val entries: List<NeighborRow>,
)

/** The query the Neighbors screen asks. Takes nothing. */
object ListNeighbors

/** Identity checks that run before the query is asked (Amendment 6, INV-NBRS-9): signed in, and the shed manager's role. */
fun Caller.requireNeighborsReader() {
    requireShedManager(
        notSignedIn = Refusal("INV-NBRS-9", "Please sign in."),
        notShedManager = Refusal("INV-NBRS-9", "Only the shed manager can see this screen."),
    )
}

/** An account as stored: its facts, and a creation sequence. Status is worked out from the facts at read time. */
data class StoredNeighbor(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val registeredAt: Instant,
    val suspended: Boolean,
    val closed: Boolean,
    val sequence: Long,
)

/** The status rules (INV-NBRS-2, INV-NBRS-4): closed wins, then suspended, otherwise active. */
fun StoredNeighbor.status(): String =
    when {
        closed -> "closed"
        suspended -> "suspended"
        else -> "active"
    }
