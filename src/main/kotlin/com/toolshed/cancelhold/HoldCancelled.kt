package com.toolshed.cancelhold

import com.toolshed.axon.HoldId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class HoldCancelled(
    @field:EventTag(key = "holdId")
    val holdId: HoldId,
    val cancelledAt: Instant,
)
