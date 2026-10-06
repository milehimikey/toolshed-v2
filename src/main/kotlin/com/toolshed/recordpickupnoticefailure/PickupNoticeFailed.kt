package com.toolshed.recordpickupnoticefailure

import com.toolshed.axon.HoldId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class PickupNoticeFailed(
    @field:EventTag(key = "holdId")
    val holdId: HoldId,
    val reason: String,
    val failedAt: Instant,
)
