package com.toolshed.suspendneighbor

import com.toolshed.axon.NeighborId
import java.time.Instant

data class NeighborSuspended(
    val neighborId: NeighborId,
    val suspendedAt: Instant,
)
