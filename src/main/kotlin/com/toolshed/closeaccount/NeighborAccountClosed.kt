package com.toolshed.closeaccount

import com.toolshed.axon.NeighborId
import java.time.Instant

data class NeighborAccountClosed(
    val neighborId: NeighborId,
    val whileSuspended: Boolean,
    val closedAt: Instant,
)
