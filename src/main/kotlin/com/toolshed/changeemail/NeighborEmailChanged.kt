package com.toolshed.changeemail

import com.toolshed.axon.NeighborId
import java.time.Instant

data class NeighborEmailChanged(
    val neighborId: NeighborId,
    val email: String,
    val changedAt: Instant,
)
