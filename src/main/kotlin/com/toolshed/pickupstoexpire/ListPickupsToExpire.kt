package com.toolshed.pickupstoexpire

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import java.time.LocalDate

/** The pickup expirer asking for its to-do list. It takes nothing and is internal: no caller identity applies. */
data object ListPickupsToExpire

/** One set-aside that is due to expire: its collect-by date has passed (INV-PTE-2). */
data class PickupToExpireRow(
    val holdId: HoldId,
    val toolId: ToolId,
    val neighborId: NeighborId,
    val collectByDate: LocalDate,
)
