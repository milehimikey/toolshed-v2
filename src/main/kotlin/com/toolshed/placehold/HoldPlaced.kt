package com.toolshed.placehold

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import java.time.Instant

data class HoldPlaced(
    val holdId: HoldId,
    val toolId: ToolId,
    val neighborId: NeighborId,
    val placedAt: Instant,
)
