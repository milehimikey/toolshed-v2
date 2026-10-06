package com.toolshed.addtool

import com.toolshed.axon.ToolId
import java.time.Instant

data class ToolAdded(
    val toolId: ToolId,
    val name: String,
    val description: String?,
    val defaultCheckoutPeriodDays: Int,
    val addedAt: Instant,
)
