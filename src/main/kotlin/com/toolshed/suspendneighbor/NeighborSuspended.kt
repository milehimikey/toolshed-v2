package com.toolshed.suspendneighbor

import com.toolshed.axon.NeighborId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class NeighborSuspended(
    @field:EventTag(key = "neighborId")
    val neighborId: NeighborId,
    val suspendedAt: Instant,
)
