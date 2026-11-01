package com.toolshed.neighbors

import com.toolshed.web.Caller
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Neighbors over REST (Amendment 3): the shed manager's screen alone (INV-NBRS-9). */
@RestController
class NeighborsController(
    private val queries: QueryGateway,
    private val zoneId: ZoneId,
) {
    @GetMapping("/api/neighbors")
    fun list(caller: Caller): List<NeighborResponse> {
        caller.requireNeighborsReader()
        return queries
            .query(ListNeighbors, Neighbors::class.java)
            .join()
            .entries
            .map { it.toResponse(zoneId) }
    }
}

data class NeighborResponse(
    val neighborId: UUID,
    val name: String,
    val email: String,
    val registeredAt: Instant,
    /** The sign-up date: the calendar date of registeredAt in the shed's zone (INV-NBRS-5). */
    val signedUpOn: LocalDate,
    val status: String,
    val canSuspend: Boolean,
)

private fun NeighborRow.toResponse(zoneId: ZoneId) =
    NeighborResponse(
        neighborId = neighborId.value,
        name = name,
        email = email,
        registeredAt = registeredAt,
        signedUpOn = registeredAt.atZone(zoneId).toLocalDate(),
        status = status,
        canSuspend = canSuspend,
    )
