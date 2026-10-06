package com.toolshed.fulfillhold

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant
import java.time.LocalDate

data class HoldFulfilled(
    @field:EventTag(key = "holdId")
    val holdId: HoldId,
    @field:EventTag(key = "toolId")
    val toolId: ToolId,
    @field:EventTag(key = "neighborId")
    val neighborId: NeighborId,
    val collectByDate: LocalDate,
    val fulfilledAt: Instant,
)
