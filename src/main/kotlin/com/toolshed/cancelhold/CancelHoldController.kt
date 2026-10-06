package com.toolshed.cancelhold

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Cancelling is deleting the hold. The holder is named in the path and must be the signed-in
 * neighbor (Amendment 6). A repeat of a cancelled hold answers 204 and records nothing new.
 */
@RestController
@RequestMapping("/api/neighbors/{neighborId}/holds/{holdId}")
class CancelHoldController(
    private val commandGateway: CommandGateway,
) {
    @DeleteMapping
    fun cancel(
        @PathVariable neighborId: UUID,
        @PathVariable holdId: UUID,
        caller: Caller,
    ): ResponseEntity<Void> {
        val command = CancelHold(NeighborId(neighborId), HoldId(holdId))
        caller.requireCanceller(command)
        commandGateway.sendAndWait(command)
        return ResponseEntity.noContent().build()
    }
}
