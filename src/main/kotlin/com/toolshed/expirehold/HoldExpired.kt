package com.toolshed.expirehold

import com.toolshed.axon.HoldId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class HoldExpired(
    @field:EventTag(key = "holdId")
    val holdId: HoldId,
    val expiredAt: Instant,
)
