package com.toolshed.registerneighbor

import com.toolshed.axon.NeighborId
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Sign-up is open to anyone, so this endpoint takes no [com.toolshed.web.Caller] (Amendment 6). */
@RestController
@RequestMapping("/api/neighbors")
class RegisterNeighborController(
    private val commandGateway: CommandGateway,
) {
    /** PUT, because the caller names the account: repeating the same request records nothing new. */
    @PutMapping("/{neighborId}")
    fun register(
        @PathVariable neighborId: UUID,
        @RequestBody body: RegisterNeighborRequest,
    ): ResponseEntity<Void> {
        commandGateway.sendAndWait(RegisterNeighbor(NeighborId(neighborId), body.name, body.email))
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}

data class RegisterNeighborRequest(
    val name: String?,
    val email: String?,
)
