package com.toolshed.myaccount

import com.toolshed.web.Caller
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * GET /api/neighbors/{neighborId}/account: the signed-in neighbor's own My Account (Amendment 3). Another
 * neighbor's id is refused (INV-MYAC-14), no sign-in is refused first (INV-MYAC-13), a malformed id is not the neighbor's own so it is refused too, an unknown id is 404.
 */
@RestController
class MyAccountController(
    private val queries: QueryGateway,
) {
    @GetMapping("/api/neighbors/{neighborId}/account")
    fun get(
        caller: Caller,
        @PathVariable neighborId: String,
    ): ResponseEntity<MyAccountResponse> {
        // Bound as text so identity is checked before the id is parsed (INV-MYAC-13 comes first).
        val query = caller.requireAccountOwner(neighborId)
        val row = queries.queryMany(query, MyAccountRow::class.java).join().firstOrNull()
        return if (row == null) ResponseEntity.notFound().build() else ResponseEntity.ok(row.toResponse())
    }
}

data class MyAccountResponse(
    val neighborId: UUID,
    val name: String,
    val email: String,
    val status: String,
    val canChangeEmail: Boolean,
    val canCloseAccount: Boolean,
)

private fun MyAccountRow.toResponse() =
    MyAccountResponse(
        neighborId = neighborId.value,
        name = name,
        email = email,
        status = status.wireName,
        canChangeEmail = canChangeEmail,
        canCloseAccount = canCloseAccount,
    )
