package com.toolshed.addtool

import com.toolshed.axon.ToolId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class ToolAdded(
    @field:EventTag(key = "toolId")
    val toolId: ToolId,
    val name: String,
    val description: String?,
    val defaultCheckoutPeriodDays: Int,
    val addedAt: Instant,
)
