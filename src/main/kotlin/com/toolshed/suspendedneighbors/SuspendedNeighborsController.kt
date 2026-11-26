package com.toolshed.suspendedneighbors

import com.toolshed.web.Caller
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/** Suspended Neighbors over REST (Amendment 3): the shed manager's screen alone (INV-SPN-11). */
@RestController
class SuspendedNeighborsController(
    private val queries: QueryGateway,
) {
    @GetMapping("/api/suspended-neighbors")
    fun list(caller: Caller): List<SuspendedNeighborResponse> {
        caller.requireSuspendedNeighborsReader()
        return queries
            .query(ListSuspendedNeighbors, SuspendedNeighbors::class.java)
            .join()
            .entries
            .map { SuspendedNeighborResponse(it.neighborId.value, it.name, it.email, it.suspendedAt) }
    }
}

data class SuspendedNeighborResponse(
    val neighborId: UUID,
    val name: String,
    val email: String,
    val suspendedAt: Instant,
)
