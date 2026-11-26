package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Neighbors' own storage (Amendment 5): in-memory, rebuilt from the event stream on restart. Every mutation leaves
 * the store as it was after the first application of the same event (INV-NBRS-7).
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

    fun suspended(
        neighborId: NeighborId,
        at: Instant,
    )

    fun reinstated(
        neighborId: NeighborId,
        at: Instant,
    )

    fun closed(neighborId: NeighborId)

    fun all(): List<StoredNeighbor>
}

class InMemoryNeighborsRepository : NeighborsRepository {
    private val lock = Any()
    private val accounts = HashMap<NeighborId, StoredNeighbor>()
    private val sequence = AtomicLong(0)

    override fun register(
        neighborId: NeighborId,
        name: String,
        email: String,
        registeredAt: Instant,
    ) = synchronized(lock) {
        // A repeated Neighbor Registered must not add an entry nor undo later changes (INV-NBRS-1, INV-NBRS-7).
        accounts.putIfAbsent(neighborId, StoredNeighbor(neighborId, name, email, registeredAt, sequence.incrementAndGet()))
        Unit
    }

    override fun changeEmail(
        neighborId: NeighborId,
        email: String,
        changedAt: Instant,
    ) = synchronized(lock) {
        // Keep the latest change only: redelivering an earlier Neighbor Email Changed must not put back a replaced
        // email (INV-NBRS-7, INV-NBRS-5).
        accounts.computeIfPresent(neighborId) { _, account ->
            val last = account.emailChangedAt
            if (last != null && changedAt <= last) account else account.copy(email = email, emailChangedAt = changedAt)
        }
        Unit
    }

    override fun suspended(
        neighborId: NeighborId,
        at: Instant,
    ) = synchronized(lock) {
        accounts.computeIfPresent(neighborId) { _, a -> a.copy(suspendedAt = latest(a.suspendedAt, at)) }
        Unit
    }

    override fun reinstated(
        neighborId: NeighborId,
        at: Instant,
    ) = synchronized(lock) {
        accounts.computeIfPresent(neighborId) { _, a -> a.copy(reinstatedAt = latest(a.reinstatedAt, at)) }
        Unit
    }

    override fun closed(neighborId: NeighborId) =
        synchronized(lock) {
            accounts.computeIfPresent(neighborId) { _, account -> account.copy(closed = true) }
            Unit
        }

    override fun all(): List<StoredNeighbor> = synchronized(lock) { accounts.values.toList() }

    private fun latest(
        current: Instant?,
        at: Instant,
    ): Instant = if (current == null || at > current) at else current
}

@Configuration
class NeighborsRepositoryConfiguration {
    @Bean
    fun neighborsRepository(): NeighborsRepository = InMemoryNeighborsRepository()
}
