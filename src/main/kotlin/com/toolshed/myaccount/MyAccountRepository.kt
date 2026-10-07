package com.toolshed.myaccount

import com.toolshed.axon.NeighborId
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/** One account as My Account knows it. Status and the two offers are derived from these facts when read. */
data class MyAccountEntry(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val suspended: Boolean = false,
    val closed: Boolean = false,
)

/** In-memory storage for My Account (Amendment 5): one entry per account, keyed by its neighbor id. */
@Repository
class MyAccountRepository {
    private val entries = ConcurrentHashMap<NeighborId, MyAccountEntry>()

    fun find(neighborId: NeighborId): MyAccountEntry? = entries[neighborId]

    /** Adds an account if it isn't there yet. A repeat changes nothing (atomic per key). */
    fun register(entry: MyAccountEntry) {
        entries.putIfAbsent(entry.neighborId, entry)
    }

    /** Applies [change] to one account in a single atomic step, so concurrent events never lose an update. */
    fun update(
        neighborId: NeighborId,
        change: (MyAccountEntry) -> MyAccountEntry,
    ) {
        entries.computeIfPresent(neighborId) { _, current -> change(current) }
    }
}
