package com.toolshed.suspendneighbor

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

/**
 * Suspend Neighbor over REST: `POST /neighbors/{neighborId}/suspension`, from the shed manager only.
 * A repeat for an already-suspended neighbor succeeds and records nothing, and still answers `201`.
 */
@RestController
class SuspendNeighborController(
    private val dispatcher: CommandDispatcher,
) {
    @PostMapping("/neighbors/{neighborId}/suspension")
    fun suspend(
        caller: Caller,
        @PathVariable neighborId: UUID,
    ): ResponseEntity<Void> {
        caller.requireShedManager(
            notSignedIn = Refusal("INV-SUS-11", "Please sign in."),
            notShedManager = Refusal("INV-SUS-1", "Only the shed manager can suspend neighbors."),
        )
        dispatcher.send(SuspendNeighbor(NeighborId(neighborId)), Any::class.java)
        return ResponseEntity.created(URI.create("/neighbors/$neighborId/suspension")).build()
    }
}
