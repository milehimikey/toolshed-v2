package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import java.time.Instant

/** The three statuses an account can show (INV-NBRS-2). */
enum class NeighborStatus(
    val label: String,
) {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    CLOSED("closed"),
}

/** One row of Neighbors (the view's field table). */
data class NeighborRow(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val registeredAt: Instant,
    val status: NeighborStatus,
    val canSuspend: Boolean,
)

/** The query's answer: every account, ordered for the screen (INV-NBRS-8). No paging. */
data class Neighbors(
    val entries: List<NeighborRow>,
)

/** The query the Neighbors screen asks. Takes nothing. */
object ListNeighbors

/** Identity checks that run before the query is asked (Amendment 6, INV-NBRS-9): signed in, then the shed manager's role. */
fun Caller.requireNeighborsReader() {
    requireShedManager(
        notSignedIn = Refusal("INV-NBRS-9", "Please sign in."),
        notShedManager = Refusal("INV-NBRS-9", "Only the shed manager can see this screen."),
    )
}

/**
 * An account as stored: the facts its status is worked out from. The latest suspension and reinstatement times are
 * kept as maxima, so applying an earlier event again never changes the outcome (INV-NBRS-7).
 */
data class StoredNeighbor(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val registeredAt: Instant,
    val sequence: Long,
    val suspendedAt: Instant? = null,
    val reinstatedAt: Instant? = null,
    val closed: Boolean = false,
    val emailChangedAt: Instant? = null,
) {
    /** The status rules, in order; the first that holds wins (INV-NBRS-2, INV-NBRS-4). */
    val status: NeighborStatus
        get() =
            when {
                closed -> NeighborStatus.CLOSED
                suspendedAt != null && (reinstatedAt == null || reinstatedAt < suspendedAt) -> NeighborStatus.SUSPENDED
                else -> NeighborStatus.ACTIVE
            }
}
