package com.toolshed.flaggedneighbors

import com.toolshed.web.Caller
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Flagged Neighbors over REST (Amendment 3): the shed manager's screen alone (INV-FLN-11). */
@RestController
class FlaggedNeighborsController(
    private val queries: QueryGateway,
) {
    @GetMapping("/api/flagged-neighbors")
    fun list(caller: Caller): List<FlaggedNeighborResponse> {
        caller.requireFlaggedNeighborsReader()
        return queries
            .query(ListFlaggedNeighbors, FlaggedNeighbors::class.java)
            .join()
            .entries
            .map { it.toResponse() }
    }
}

data class LateReturnResponse(
    val loanId: UUID,
    val toolName: String,
    val dueDate: LocalDate,
    val returnedOn: LocalDate,
    val flaggedAt: Instant,
)

data class FlaggedNeighborResponse(
    val neighborId: UUID,
    val name: String,
    val email: String,
    val flagCount: Int,
    val latestFlaggedAt: Instant,
    val lateReturns: List<LateReturnResponse>,
)

private fun FlaggedNeighborRow.toResponse() =
    FlaggedNeighborResponse(
        neighborId = neighborId.value,
        name = name,
        email = email,
        flagCount = flagCount,
        latestFlaggedAt = latestFlaggedAt,
        lateReturns = lateReturns.map { LateReturnResponse(it.loanId.value, it.toolName, it.dueDate, it.returnedOn, it.flaggedAt) },
    )
