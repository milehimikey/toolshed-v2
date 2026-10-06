package com.toolshed.reinstateneighbor

import com.toolshed.axon.NeighborId
import java.time.Instant

data class NeighborReinstated(
    val neighborId: NeighborId,
    val reinstatedAt: Instant,
)
