package com.toolshed.suspendedneighbors

import com.toolshed.web.Caller
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Suspended Neighbors over REST (Amendment 3): the shed manager's screen alone (INV-SPN-11). */
@RestController
class SuspendedNeighborsController(
    private val queries: QueryGateway,
    private val zone: ZoneId,
) {
    @GetMapping("/api/suspended-neighbors")
    fun list(caller: Caller): List<SuspendedNeighborResponse> {
        caller.requireSuspendedNeighborsReader()
        return queries
            .query(ListSuspendedNeighbors, SuspendedNeighbors::class.java)
            .join()
            .entries
            .map {
                SuspendedNeighborResponse(
                    neighborId = it.neighborId.value,
                    name = it.name,
                    email = it.email,
                    suspendedAt = it.suspendedAt,
                    suspendedOn = it.suspendedAt.atZone(zone).toLocalDate(),
                )
            }
    }
}

/** suspendedOn is the suspended date in the shed's local time zone, as the screen shows it. */
data class SuspendedNeighborResponse(
    val neighborId: UUID,
    val name: String,
    val email: String,
    val suspendedAt: Instant,
    val suspendedOn: LocalDate,
)
