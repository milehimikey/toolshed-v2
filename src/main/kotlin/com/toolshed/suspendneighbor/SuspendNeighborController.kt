package com.toolshed.suspendneighbor

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Suspend Neighbor over REST: `PUT /api/neighbors/{neighborId}/suspension`, from the shed manager only. Sets state on an existing neighbor, so `204`.
 * PUT is the idempotent state-setting verb: a repeat for an already-suspended neighbor succeeds and records nothing, and still answers `201`.
 */
@RestController
class SuspendNeighborController(
    private val dispatcher: CommandDispatcher,
) {
    @PutMapping("/api/neighbors/{neighborId}/suspension")
    fun suspend(
        caller: Caller,
        @PathVariable neighborId: UUID,
    ): ResponseEntity<Void> {
        caller.requireShedManager(
            notSignedIn = Refusal("INV-SUS-11", "Please sign in."),
            notShedManager = Refusal("INV-SUS-1", "Only the shed manager can suspend neighbors."),
        )
        dispatcher.send(SuspendNeighbor(NeighborId(neighborId)), Any::class.java)
        return ResponseEntity.noContent().build()
    }
}
