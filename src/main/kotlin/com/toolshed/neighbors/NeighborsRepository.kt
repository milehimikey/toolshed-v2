package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** One account as the Neighbors view knows it. Status and canSuspend are derived from these facts when read. */
data class NeighborEntry(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val registeredAt: Instant,
    val suspended: Boolean = false,
    val closed: Boolean = false,
)

/** In-memory storage for the Neighbors view (Amendment 5): one entry per account, keyed by its neighbor id. */
@Repository
class NeighborsRepository {
    private val entries = ConcurrentHashMap<NeighborId, NeighborEntry>()

    fun find(neighborId: NeighborId): NeighborEntry? = entries[neighborId]

    fun save(entry: NeighborEntry) {
        entries[entry.neighborId] = entry
    }

    fun all(): List<NeighborEntry> = entries.values.toList()
}
