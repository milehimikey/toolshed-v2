package com.toolshed.myaccount

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/** GET /api/my-account/{neighborId}: the signed-in neighbor's own account. Another neighbor's id is refused (INV-MYAC-14). */
@RestController
class MyAccountController(
    private val queries: QueryGateway,
) {
    @GetMapping("/api/my-account/{neighborId}")
    fun get(
        caller: Caller,
        @PathVariable neighborId: UUID,
    ): MyAccountRow {
        caller.requireNeighbor(
            neighborId = NeighborId(neighborId),
            notSignedIn = Refusal("INV-MYAC-13", "Please sign in."),
            mismatch = Refusal("INV-MYAC-14", "You can only see your own account."),
        )
        val lookup = queries.query(GetMyAccount(NeighborId(neighborId)), MyAccountLookup::class.java).join()
        return lookup.account ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "There is no such account.")
    }
}
