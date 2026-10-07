package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import org.springframework.stereotype.Component
import java.time.Instant

/** One row of the Neighbors screen. */
data class NeighborRow(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val registeredAt: Instant,
    val status: String,
    val canSuspend: Boolean,
)

/**
 * The Neighbors list. Status follows the doc's rules in order: closed first, then suspended, then active (INV-NBRS-2).
 * Rows are ordered by name ignoring case, then by sign-up time (INV-NBRS-8).
 */
@Component
class NeighborsView(
    private val repository: NeighborsRepository,
) {
    fun list(): List<NeighborRow> =
        repository
            .all()
            .map { it.toRow() }
            .sortedWith(compareBy<NeighborRow> { it.name.lowercase() }.thenBy { it.registeredAt })

    private fun NeighborEntry.toRow(): NeighborRow {
        val status =
            when {
                closed -> "closed"
                suspended -> "suspended"
                else -> "active"
            }
        return NeighborRow(neighborId, name, email, registeredAt, status, canSuspend = status == "active")
    }
}
