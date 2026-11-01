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

/** GET /api/neighbors/{neighborId}/account: the signed-in neighbor's own My Account. Another neighbor's id is refused (INV-MYAC-14). */
@RestController
class MyAccountController(
    private val queries: QueryGateway,
) {
    @GetMapping("/api/neighbors/{neighborId}/account")
    fun get(
        caller: Caller,
        @PathVariable neighborId: UUID,
    ): MyAccountRow {
        val neighbor = NeighborId(neighborId)
        caller.requireNeighbor(
            neighborId = neighbor,
            notSignedIn = Refusal("INV-MYAC-13", "Please sign in."),
            mismatch = Refusal("INV-MYAC-14", "You can only see your own account."),
        )
        return queries.query(GetMyAccount(neighbor), MyAccount::class.java).join().account
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No account was found.")
    }
}
