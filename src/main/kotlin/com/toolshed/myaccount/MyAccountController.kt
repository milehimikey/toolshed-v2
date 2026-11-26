package com.toolshed.myaccount

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor
import com.toolshed.web.requireSignedIn
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * GET /api/neighbors/{neighborId}/account: the signed-in neighbor's own My Account. The checks run in the owner's
 * order of 2026-11-20: not-signed-in (INV-MYAC-13), then a malformed id as bad input (400), then requester-mismatch
 * (INV-MYAC-14). The id is therefore taken as text and parsed only once the caller is known to be signed in.
 * No account with that id is 404.
 */
@RestController
class MyAccountController(
    private val queries: QueryGateway,
) {
    private val notSignedIn = Refusal("INV-MYAC-13", "Please sign in.")
    private val mismatch = Refusal("INV-MYAC-14", "You can only see your own account.")

    @GetMapping("/api/neighbors/{neighborId}/account")
    fun get(
        caller: Caller,
        @PathVariable neighborId: String,
    ): ResponseEntity<MyAccount> {
        caller.requireSignedIn(notSignedIn)
        val neighbor = NeighborId(parse(neighborId))
        caller.requireNeighbor(neighbor, notSignedIn, mismatch)
        val answer = queries.query(GetMyAccount(neighbor), MyAccountAnswer::class.java).join()
        val account = answer.account ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(account)
    }

    private fun parse(raw: String): UUID =
        try {
            UUID.fromString(raw.trim()).takeIf { CANONICAL.matches(raw.trim()) } ?: throw IllegalArgumentException()
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed neighbor id", e)
        }

    private companion object {
        val CANONICAL = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
