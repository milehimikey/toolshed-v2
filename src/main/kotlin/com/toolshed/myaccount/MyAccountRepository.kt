package com.toolshed.myaccount

import com.toolshed.axon.NeighborId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * My Account's own storage (Amendment 5): in-memory, rebuilt from the event stream on restart. Every mutation is
 * idempotent (INV-MYAC-10), and closed is final: nothing changes a closed account.
 */
interface MyAccountRepository {
    fun register(
        neighborId: NeighborId,
        name: String,
        email: String,
    )

    fun changeEmail(
        neighborId: NeighborId,
        email: String,
    )

    /** Neighbor Suspended at [at]; an event already applied is a no-op (INV-MYAC-10). */
    fun suspend(
        neighborId: NeighborId,
        at: Instant,
    )

    /** Neighbor Reinstated at [at]; an event already applied is a no-op (INV-MYAC-10). */
    fun reinstate(
        neighborId: NeighborId,
        at: Instant,
    )

    fun close(neighborId: NeighborId)

    /** The one account with this ID (INV-MYAC-1), or null. */
    fun find(neighborId: NeighborId): AccountEntry?
}

class InMemoryMyAccountRepository : MyAccountRepository {
    private val accounts = ConcurrentHashMap<NeighborId, AccountEntry>()

    override fun register(
        neighborId: NeighborId,
        name: String,
        email: String,
    ) {
        // INV-MYAC-10: a repeated Neighbor Registered leaves one account, as it was.
        accounts.putIfAbsent(neighborId, AccountEntry(neighborId, name, email))
    }

    override fun changeEmail(
        neighborId: NeighborId,
        email: String,
    ) = update(neighborId) { it.copy(email = email) }

    // Each suspend/reinstate event is identified by its neighbor, kind and time, so a redelivery that arrives after a
    // later event can't flip the status back (INV-MYAC-10).
    private val statusEventsSeen = ConcurrentHashMap.newKeySet<Triple<NeighborId, Boolean, Instant>>()

    override fun suspend(
        neighborId: NeighborId,
        at: Instant,
    ) {
        if (statusEventsSeen.add(Triple(neighborId, true, at))) update(neighborId) { it.copy(suspended = true) }
    }

    override fun reinstate(
        neighborId: NeighborId,
        at: Instant,
    ) {
        if (statusEventsSeen.add(Triple(neighborId, false, at))) update(neighborId) { it.copy(suspended = false) }
    }

    override fun close(neighborId: NeighborId) = update(neighborId) { it.copy(closed = true) }

    override fun find(neighborId: NeighborId): AccountEntry? = accounts[neighborId]

    /** Closed is final: a replayed event for a closed account changes nothing. An unknown account is ignored. */
    private fun update(
        neighborId: NeighborId,
        change: (AccountEntry) -> AccountEntry,
    ) {
        accounts.computeIfPresent(neighborId) { _, account -> if (account.closed) account else change(account) }
    }
}

@Configuration
class MyAccountRepositoryConfiguration {
    @Bean
    fun myAccountRepository(): MyAccountRepository = InMemoryMyAccountRepository()
}
