package com.toolshed.completetoolmaintenance

import com.toolshed.axon.ToolId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class ToolMaintenanceCompleted(
    @field:EventTag(key = "toolId")
    val toolId: ToolId,
    val completedAt: Instant,
)
