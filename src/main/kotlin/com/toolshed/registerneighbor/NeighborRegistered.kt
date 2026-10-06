package com.toolshed.registerneighbor

import com.toolshed.axon.NeighborId
import java.time.Instant

data class NeighborRegistered(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val registeredAt: Instant,
)
