package com.toolshed.myholds

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** My Holds over REST (Amendment 3): the neighbor is named in the path and must be the signed-in neighbor. */
@RestController
@RequestMapping("/api/neighbors/{neighborId}/holds")
class MyHoldsController(
    private val queryHandler: ListMyHoldsQueryHandler,
) {
    @GetMapping
    fun list(
        @PathVariable neighborId: UUID,
        caller: Caller,
    ): List<MyHoldsResponse> {
        val query = ListMyHolds(NeighborId(neighborId))
        caller.requireLister(query)
        return queryHandler.handle(query).map { it.toResponse() }
    }
}

data class MyHoldsResponse(
    val holdId: UUID,
    val neighborId: UUID,
    val toolId: UUID,
    val toolName: String,
    val placedAt: Instant,
    val status: String,
    val position: Int?,
    val collectByDate: LocalDate?,
    val pickupNotice: String,
    val canCancel: Boolean,
)

private fun MyHoldsRow.toResponse() =
    MyHoldsResponse(
        holdId = holdId.value,
        neighborId = neighborId.value,
        toolId = toolId.value,
        toolName = toolName,
        placedAt = placedAt,
        status = status.wireName,
        position = position,
        collectByDate = collectByDate,
        pickupNotice = pickupNotice.wireName,
        canCancel = canCancel,
    )
