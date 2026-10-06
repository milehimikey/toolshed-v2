package com.toolshed.registerneighbor

import com.toolshed.axon.NeighborId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class NeighborRegistered(
    @field:EventTag(key = "neighborId")
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val registeredAt: Instant,
)
