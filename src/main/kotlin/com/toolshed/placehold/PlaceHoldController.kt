package com.toolshed.placehold

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The holder is named in the path and must be the signed-in neighbor (Amendment 6). The hold is
 * named by the caller, so a repeat with the same tool and neighbor records nothing new and still
 * answers 201, as the other sign-up style commands do.
 */
@RestController
@RequestMapping("/api/neighbors/{neighborId}/holds/{holdId}")
class PlaceHoldController(
    private val dispatcher: CommandDispatcher,
) {
    @PutMapping
    fun place(
        @PathVariable neighborId: UUID,
        @PathVariable holdId: UUID,
        caller: Caller,
        @RequestBody body: PlaceHoldRequest,
    ): ResponseEntity<Void> {
        val command = PlaceHold(HoldId(holdId), ToolId(body.toolId), NeighborId(neighborId))
        caller.requireHolder(command)
        dispatcher.send(command, Any::class.java)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}

data class PlaceHoldRequest(
    val toolId: UUID,
)
