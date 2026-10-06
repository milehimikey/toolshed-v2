package com.toolshed.reinstateneighbor

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** DELETE /api/neighbors/{neighborId}/suspension: undoes a singleton suspension. A malformed id is bad input (400), before any rule. */
@RestController
class ReinstateNeighborController(
    private val dispatcher: CommandDispatcher,
) {
    @DeleteMapping("/api/neighbors/{neighborId}/suspension")
    fun reinstate(
        caller: Caller,
        @PathVariable neighborId: UUID,
    ): ResponseEntity<Void> {
        caller.requireShedManager(
            notSignedIn = Refusal("INV-REI-9", "Please sign in."),
            notShedManager = Refusal("INV-REI-1", "Only the shed manager can reinstate neighbors."),
        )
        dispatcher.send(ReinstateNeighbor(NeighborId(neighborId)), Any::class.java)
        return ResponseEntity.noContent().build()
    }
}
