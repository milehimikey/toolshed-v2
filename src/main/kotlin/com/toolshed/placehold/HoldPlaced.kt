package com.toolshed.placehold

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class HoldPlaced(
    @field:EventTag(key = "holdId")
    val holdId: HoldId,
    @field:EventTag(key = "toolId")
    val toolId: ToolId,
    @field:EventTag(key = "neighborId")
    val neighborId: NeighborId,
    val placedAt: Instant,
)
