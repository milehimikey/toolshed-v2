package com.toolshed.myaccount

import com.toolshed.axon.NeighborId
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * The in-memory read model behind My Account (Amendment 5): one row per account. Every apply is idempotent
 * (INV-MYAC-10). Status is worked out from the current facts when a row is read, closed first, then suspended
 * (status rules), so closed stays final whatever arrives after it.
 */
@Repository
class MyAccountRepository {
    private data class Account(
        val neighborId: NeighborId,
        val name: String,
        val email: String,
        val suspended: Boolean = false,
        val closed: Boolean = false,
    )

    private val accounts = ConcurrentHashMap<NeighborId, Account>()

    /** One account per Neighbor Registered; a repeat leaves it as it was (INV-MYAC-3, INV-MYAC-10). */
    fun register(
        neighborId: NeighborId,
        name: String,
        email: String,
    ) {
        accounts.putIfAbsent(neighborId, Account(neighborId, name, email))
    }

    /** A closed account never changes again, so a replayed change on it does nothing. */
    fun changeEmail(
        neighborId: NeighborId,
        email: String,
    ) {
        accounts.computeIfPresent(neighborId) { _, a -> if (a.closed) a else a.copy(email = email) }
    }

    fun suspend(neighborId: NeighborId) {
        accounts.computeIfPresent(neighborId) { _, a -> if (a.closed) a else a.copy(suspended = true) }
    }

    fun reinstate(neighborId: NeighborId) {
        accounts.computeIfPresent(neighborId) { _, a -> if (a.closed) a else a.copy(suspended = false) }
    }

    fun close(neighborId: NeighborId) {
        accounts.computeIfPresent(neighborId) { _, a -> a.copy(closed = true) }
    }

    /** The account with this neighbor id, or null. Never another account (INV-MYAC-1). */
    fun find(neighborId: NeighborId): MyAccount? =
        accounts[neighborId]?.let { a ->
            val status =
                when {
                    a.closed -> "closed"
                    a.suspended -> "suspended"
                    else -> "active"
                }
            MyAccount(
                neighborId = a.neighborId,
                name = a.name,
                email = a.email,
                status = status,
                canChangeEmail = status != "closed",
                canCloseAccount = status != "closed",
            )
        }
}
