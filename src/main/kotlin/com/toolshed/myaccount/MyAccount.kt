package com.toolshed.myaccount

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor
import com.toolshed.web.requireSignedIn
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/** Where an account stands, as My Account shows it. The status rules decide which one applies. */
enum class AccountStatus(
    val wireName: String,
) {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    CLOSED("closed"),
}

/** The facts the view keeps for one account, one per Neighbor Registered. */
data class AccountEntry(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val suspended: Boolean = false,
    val closed: Boolean = false,
)

/** One row of My Account (the view's field table). Status and the two offers are derived from the facts. */
data class MyAccountRow(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val status: AccountStatus,
    val canChangeEmail: Boolean,
    val canCloseAccount: Boolean,
)

/** Get my account: the asking neighbor's own account, by neighbor ID (INV-MYAC-1). */
data class GetMyAccount(
    val neighborId: NeighborId,
)

/**
 * The checks on a raw path id, in the owner's ruling order: no sign-in first (INV-MYAC-13), then a malformed id is
 * bad input (400, Amendment 7), then another neighbor's well-formed id is a mismatch (INV-MYAC-14).
 */
fun Caller.requireAccountOwner(rawNeighborId: String): GetMyAccount {
    requireSignedIn(Refusal("INV-MYAC-13", "Please sign in."))
    val id =
        runCatching { UUID.fromString(rawNeighborId) }.getOrNull()
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "neighborId must be a UUID")
    val query = GetMyAccount(NeighborId(id))
    requireAccountOwner(query)
    return query
}

/** Identity checks that run before the query is asked (Amendment 6): INV-MYAC-13, then INV-MYAC-14. */
fun Caller.requireAccountOwner(query: GetMyAccount) {
    requireNeighbor(
        neighborId = query.neighborId,
        notSignedIn = Refusal("INV-MYAC-13", "Please sign in."),
        mismatch = Refusal("INV-MYAC-14", "You can only see your own account."),
    )
}
