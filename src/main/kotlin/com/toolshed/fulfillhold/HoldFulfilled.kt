package com.toolshed.fulfillhold

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import java.time.Instant
import java.time.LocalDate

data class HoldFulfilled(
    val holdId: HoldId,
    val toolId: ToolId,
    val neighborId: NeighborId,
    val collectByDate: LocalDate,
    val fulfilledAt: Instant,
)
