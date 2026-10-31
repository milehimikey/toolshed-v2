package com.toolshed.pickupnoticestosend

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import java.time.LocalDate

/** The pickup notice gateway asking for its to-do list. It takes nothing and is internal: no caller identity applies. */
data object ListPickupsToNotify

/** One hold on the list, with everything the pickup email needs (INV-PNS-2). */
data class PickupNoticesToSendRow(
    val holdId: HoldId,
    val toolId: ToolId,
    val neighborId: NeighborId,
    val collectByDate: LocalDate,
    val holderName: String,
    val holderEmail: String,
    val toolName: String,
)
