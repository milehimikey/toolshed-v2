package com.toolshed.holdstofulfill

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId

/** The hold fulfiller asking for its to-do list. It takes nothing and is internal: no caller identity applies. */
data object ListToolsToFulfill

/** One tool on the list, with the hold at the front of its queue (INV-HTF-2). */
data class HoldsToFulfillRow(
    val toolId: ToolId,
    val holdId: HoldId,
    val neighborId: NeighborId,
)
