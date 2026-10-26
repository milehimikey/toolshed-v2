package com.toolshed.myholds

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import java.time.Instant
import java.time.LocalDate

/** Whether a pickup email has been sent for a set-aside, as My Holds shows it (INV-MYHD-13). */
enum class PickupNotice(
    val wireName: String,
) {
    NONE("none"),
    SENT("sent"),
    UNDELIVERED("undelivered"),
}

/** Where a hold stands, as My Holds shows it. The status rules decide which one applies. */
enum class HoldViewStatus(
    val wireName: String,
) {
    WAITING("waiting"),
    READY_FOR_PICKUP("ready-for-pickup"),
    PICKUP_WINDOW_ENDED("pickup-window-ended"),
}

/**
 * A hold the repository keeps while it is live. [sequence] is the order Hold Placed was recorded in, which decides
 * the queue (INV-MYHD-2) even when two holds share a [placedAt] instant.
 */
data class HoldEntry(
    val holdId: HoldId,
    val toolId: ToolId,
    val neighborId: NeighborId,
    val placedAt: Instant,
    val sequence: Long,
    val fulfilled: Boolean = false,
    val collectByDate: LocalDate? = null,
    val pickupNotice: PickupNotice = PickupNotice.NONE,
)

/** One row of My Holds (the view's field table), derived at read time. */
data class MyHoldsRow(
    val holdId: HoldId,
    val neighborId: NeighborId,
    val toolId: ToolId,
    val toolName: String,
    val placedAt: Instant,
    val status: HoldViewStatus,
    val position: Int?,
    val collectByDate: LocalDate?,
    val pickupNotice: PickupNotice,
    val canCancel: Boolean,
)
