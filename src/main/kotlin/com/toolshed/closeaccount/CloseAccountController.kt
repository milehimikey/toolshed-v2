package com.toolshed.closeaccount

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** PUT /api/neighbors/{neighborId}/closure: closes the neighbor's own account. A malformed id is bad input (400). Sets state on an existing account, so 204 (success-status ruling). */
@RestController
class CloseAccountController(
    private val dispatcher: CommandDispatcher,
) {
    @PutMapping("/api/neighbors/{neighborId}/closure")
    fun close(
        caller: Caller,
        @PathVariable neighborId: UUID,
    ): ResponseEntity<Void> {
        caller.requireNeighbor(
            neighborId = NeighborId(neighborId),
            notSignedIn = Refusal("INV-CLS-1", "Please sign in."),
            mismatch = Refusal("INV-CLS-1", "You can only close your own account."),
        )
        dispatcher.send(CloseAccount(NeighborId(neighborId)), Any::class.java)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }
}
