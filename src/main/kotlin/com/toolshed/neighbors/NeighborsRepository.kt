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
        changedAt: Instant,
    )

    fun suspend(
        neighborId: NeighborId,
        suspendedAt: Instant,
    )

    fun reinstate(
        neighborId: NeighborId,
        reinstatedAt: Instant,
    )

    fun close(neighborId: NeighborId)

    fun all(): List<StoredNeighbor>
}

class InMemoryNeighborsRepository : NeighborsRepository {
    private val lock = Any()
    private val neighbors = HashMap<NeighborId, StoredNeighbor>()
    private val sequence = AtomicLong(0)

    // Suspension events already applied, so a redelivery never undoes a later one (INV-NBRS-7).
    private val appliedSuspensions = HashSet<Triple<NeighborId, Boolean, Instant>>()

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
        changedAt: Instant,
    ) = update(neighborId) {
        // An older change redelivered after a newer one must not win (INV-NBRS-5, INV-NBRS-7).
        val latest = it.emailChangedAt
        if (latest != null && changedAt.isBefore(latest)) it else it.copy(email = email, emailChangedAt = changedAt)
    }

    override fun suspend(
        neighborId: NeighborId,
        suspendedAt: Instant,
    ) = setSuspended(neighborId, true, suspendedAt)

    override fun reinstate(
        neighborId: NeighborId,
        reinstatedAt: Instant,
    ) = setSuspended(neighborId, false, reinstatedAt)

    private fun setSuspended(
        neighborId: NeighborId,
        suspended: Boolean,
        at: Instant,
    ) = synchronized(lock) {
        val firstTime = appliedSuspensions.add(Triple(neighborId, suspended, at))
        update(neighborId) {
            val latest = it.suspensionChangedAt
            if (!firstTime || (latest != null && at.isBefore(latest))) {
                it
            } else {
                it.copy(suspended = suspended, suspensionChangedAt = at)
            }
        }
    }

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
