package com.toolshed.recordpickupnoticefailure

import com.toolshed.axon.HoldId
import java.time.Instant

data class PickupNoticeFailed(
    val holdId: HoldId,
    val reason: String,
    val failedAt: Instant,
)
