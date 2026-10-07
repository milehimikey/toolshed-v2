package com.toolshed.myaccount

import com.toolshed.axon.NeighborId
import org.springframework.stereotype.Component

/** One neighbor's own account, as the My Account screen shows it. */
data class MyAccountRow(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val status: String,
    val canChangeEmail: Boolean,
    val canCloseAccount: Boolean,
)

/**
 * The account for one neighbor id, or none. Status follows the doc's rules in order: closed, then suspended, then active
 * (INV-MYAC-4, INV-MYAC-5). Change Email and Close Account are offered on any account that isn't closed (INV-MYAC-9, 11).
 */
@Component
class MyAccountView(
    private val repository: MyAccountRepository,
) {
    fun get(neighborId: NeighborId): MyAccountRow? {
        val entry = repository.find(neighborId) ?: return null
        val status =
            when {
                entry.closed -> "closed"
                entry.suspended -> "suspended"
                else -> "active"
            }
        val offered = status != "closed"
        return MyAccountRow(entry.neighborId, entry.name, entry.email, status, canChangeEmail = offered, canCloseAccount = offered)
    }
}
