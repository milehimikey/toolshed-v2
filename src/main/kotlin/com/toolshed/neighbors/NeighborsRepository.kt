package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Neighbors' own storage (Amendment 5): in-memory, rebuilt from the event stream on restart. Every mutation
 * leaves the store as it was after the first application of the same event (INV-NBRS-7).
 */
interface NeighborsRepository {
    fun register(
        neighborId: NeighborId,
        name: String,
        email: String,
        registeredAt: Instant,
    )

    fun changeEmail(
        neighborId: NeighborId,
        email: String,
    )

    fun suspend(neighborId: NeighborId)

    fun reinstate(neighborId: NeighborId)

    fun close(neighborId: NeighborId)

    fun all(): List<StoredNeighbor>
}

class InMemoryNeighborsRepository : NeighborsRepository {
    private val lock = Any()
    private val neighbors = HashMap<NeighborId, StoredNeighbor>()
    private val sequence = AtomicLong(0)

    override fun register(
        neighborId: NeighborId,
        name: String,
        email: String,
        registeredAt: Instant,
    ) = synchronized(lock) {
        // A repeated Neighbor Registered must not undo a later change: the first application wins (INV-NBRS-7).
        neighbors.putIfAbsent(
            neighborId,
            StoredNeighbor(neighborId, name, email, registeredAt, suspended = false, closed = false, sequence = sequence.incrementAndGet()),
        )
        Unit
    }

    override fun changeEmail(
        neighborId: NeighborId,
        email: String,
    ) = update(neighborId) { it.copy(email = email) }

    override fun suspend(neighborId: NeighborId) = update(neighborId) { it.copy(suspended = true) }

    override fun reinstate(neighborId: NeighborId) = update(neighborId) { it.copy(suspended = false) }

    // Closed is final (INV-NBRS-4): nothing ever clears the flag.
    override fun close(neighborId: NeighborId) = update(neighborId) { it.copy(closed = true) }

    override fun all(): List<StoredNeighbor> = synchronized(lock) { neighbors.values.toList() }

    private fun update(
        neighborId: NeighborId,
        change: (StoredNeighbor) -> StoredNeighbor,
    ) = synchronized(lock) {
        neighbors.computeIfPresent(neighborId) { _, stored -> change(stored) }
        Unit
    }
}

@Configuration
class NeighborsRepositoryConfiguration {
    @Bean
    fun neighborsRepository(): NeighborsRepository = InMemoryNeighborsRepository()
}
