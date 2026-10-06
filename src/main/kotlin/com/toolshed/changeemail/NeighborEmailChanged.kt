package com.toolshed.changeemail

import com.toolshed.axon.NeighborId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class NeighborEmailChanged(
    @field:EventTag(key = "neighborId")
    val neighborId: NeighborId,
    val email: String,
    val changedAt: Instant,
)
