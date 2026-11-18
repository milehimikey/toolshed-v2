package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import java.time.Instant

/** The three statuses of an account (INV-NBRS-2). */
enum class NeighborStatus(
    val text: String,
) {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    CLOSED("closed"),
}

/** One row of Neighbors (the view's field table). canSuspend is derived: yes while active (INV-NBRS-6). */
data class NeighborRow(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val registeredAt: Instant,
    val status: NeighborStatus,
) {
    val canSuspend: Boolean
        get() = status == NeighborStatus.ACTIVE
}

/** The query's answer: every account, ordered for the screen (INV-NBRS-8). No paging. */
data class Neighbors(
    val entries: List<NeighborRow>,
)

/** The query the Neighbors screen asks. Takes nothing. */
object ListNeighbors

/** Identity checks that run before the query is asked (Amendment 6, INV-NBRS-9). */
fun Caller.requireNeighborsReader() {
    requireShedManager(
        notSignedIn = Refusal("INV-NBRS-9", "Please sign in."),
        notShedManager = Refusal("INV-NBRS-9", "Only the shed manager can see this screen."),
    )
}

/** An account as stored: its facts, and a creation sequence for stable ordering. */
data class StoredNeighbor(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val registeredAt: Instant,
    val suspended: Boolean,
    val closed: Boolean,
    val sequence: Long,
    val emailChangedAt: Instant? = null,
    val suspensionChangedAt: Instant? = null,
) {
    /** Status rules, first that holds wins: closed, then suspended, then active (INV-NBRS-2, 4). */
    val status: NeighborStatus
        get() =
            when {
                closed -> NeighborStatus.CLOSED
                suspended -> NeighborStatus.SUSPENDED
                else -> NeighborStatus.ACTIVE
            }
}
