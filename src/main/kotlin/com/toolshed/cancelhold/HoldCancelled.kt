package com.toolshed.cancelhold

import com.toolshed.axon.HoldId
import java.time.Instant

data class HoldCancelled(
    val holdId: HoldId,
    val cancelledAt: Instant,
)
