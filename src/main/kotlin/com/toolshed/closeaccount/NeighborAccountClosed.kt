package com.toolshed.closeaccount

import com.toolshed.axon.NeighborId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class NeighborAccountClosed(
    @field:EventTag(key = "neighborId")
    val neighborId: NeighborId,
    val whileSuspended: Boolean,
    val closedAt: Instant,
)
