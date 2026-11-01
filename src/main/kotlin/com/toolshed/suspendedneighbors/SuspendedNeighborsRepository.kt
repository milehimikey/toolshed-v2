package com.toolshed.suspendedneighbors

import com.toolshed.axon.NeighborId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Suspended Neighbors' own storage (Amendment 5): in-memory, rebuilt from the event stream on restart. Every
 * mutation leaves the store as it was after the first application of the same event (INV-SPN-9).
 */
interface SuspendedNeighborsRepository {
    fun saveNeighbor(
        neighborId: NeighborId,
        name: String,
        email: String,
    )

    fun changeEmail(
        neighborId: NeighborId,
        email: String,
    )

    fun suspend(
        neighborId: NeighborId,
        suspendedAt: Instant,
    )

    fun remove(neighborId: NeighborId)

    fun entries(): List<StoredSuspension>

    fun neighborName(neighborId: NeighborId): String?

    fun neighborEmail(neighborId: NeighborId): String?
}

class InMemorySuspendedNeighborsRepository : SuspendedNeighborsRepository {
    private val lock = Any()
    private val names = HashMap<NeighborId, String>()
    private val emails = HashMap<NeighborId, String>()
    private val entries = HashMap<NeighborId, StoredSuspension>()
    private val appliedSuspensions = HashSet<Pair<NeighborId, Instant>>()
    private val sequence = AtomicLong(0)

    override fun saveNeighbor(
        neighborId: NeighborId,
        name: String,
        email: String,
    ) = synchronized(lock) {
        names[neighborId] = name
        // A repeated Neighbor Registered must not undo a later Neighbor Email Changed already applied.
        emails.putIfAbsent(neighborId, email)
        Unit
    }

    override fun changeEmail(
        neighborId: NeighborId,
        email: String,
    ) = synchronized(lock) {
        emails[neighborId] = email
        Unit
    }

    override fun suspend(
        neighborId: NeighborId,
        suspendedAt: Instant,
    ) = synchronized(lock) {
        // INV-SPN-9: the same Neighbor Suspended applied again changes nothing, even after the entry is gone.
        if (!appliedSuspensions.add(neighborId to suspendedAt)) return@synchronized Unit
        // INV-SPN-6: at most one entry per neighbor.
        entries.putIfAbsent(neighborId, StoredSuspension(neighborId, suspendedAt, sequence.incrementAndGet()))
        Unit
    }

    override fun remove(neighborId: NeighborId) =
        synchronized(lock) {
            entries.remove(neighborId)
            Unit
        }

    override fun entries(): List<StoredSuspension> = synchronized(lock) { entries.values.toList() }

    override fun neighborName(neighborId: NeighborId): String? = synchronized(lock) { names[neighborId] }

    override fun neighborEmail(neighborId: NeighborId): String? = synchronized(lock) { emails[neighborId] }
}

@Configuration
class SuspendedNeighborsRepositoryConfiguration {
    @Bean
    fun suspendedNeighborsRepository(): SuspendedNeighborsRepository = InMemorySuspendedNeighborsRepository()
}
