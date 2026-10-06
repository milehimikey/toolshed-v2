package com.toolshed.changeemail

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The account is named in the path and must be the signed-in neighbor (Amendment 6). Changing the
 * email is not a creation, so success answers 204, including a repeat that records nothing new.
 */
@RestController
@RequestMapping("/api/neighbors/{neighborId}/email")
class ChangeEmailController(
    private val commandGateway: CommandGateway,
) {
    @PutMapping
    fun change(
        @PathVariable neighborId: UUID,
        caller: Caller,
        @RequestBody body: ChangeEmailRequest,
    ): ResponseEntity<Void> {
        val command = ChangeEmail(NeighborId(neighborId), body.email)
        caller.requireAccountHolder(command)
        commandGateway.sendAndWait(command)
        return ResponseEntity.noContent().build()
    }
}

data class ChangeEmailRequest(
    val email: String?,
)
