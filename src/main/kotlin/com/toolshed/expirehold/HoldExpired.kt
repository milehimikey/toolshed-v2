package com.toolshed.expirehold

import com.toolshed.axon.HoldId
import java.time.Instant

data class HoldExpired(
    val holdId: HoldId,
    val expiredAt: Instant,
)
