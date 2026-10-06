package com.toolshed.reinstateneighbor

import com.toolshed.axon.NeighborId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class NeighborReinstated(
    @field:EventTag(key = "neighborId")
    val neighborId: NeighborId,
    val reinstatedAt: Instant,
)
