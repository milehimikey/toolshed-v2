package com.toolshed.myaccount

import com.toolshed.axon.NeighborId
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * The in-memory read model behind My Account (Amendment 5). One row per account. Every apply is idempotent
 * (INV-MYAC-10). Suspension and closure are kept as separate facts and the status is worked out when a row is read,
 * in the doc's order: closed, then suspended, then active.
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

    /** Neighbor Registered: opens the account. A replay keeps the account as it is (INV-MYAC-3, INV-MYAC-10). */
    fun register(
        neighborId: NeighborId,
        name: String,
        email: String,
    ) {
        accounts.putIfAbsent(neighborId, Account(neighborId, name, email))
    }

    /** Neighbor Email Changed: replaces the email. A closed account is final, so a replay for it changes nothing. */
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

    /** The one account asked for, or null (INV-MYAC-1). */
    fun find(neighborId: NeighborId): MyAccountRow? =
        accounts[neighborId]?.let { a ->
            val status =
                when {
                    a.closed -> "closed"
                    a.suspended -> "suspended"
                    else -> "active"
                }
            MyAccountRow(a.neighborId, a.name, a.email, status, canChangeEmail = !a.closed, canCloseAccount = !a.closed)
        }
}
