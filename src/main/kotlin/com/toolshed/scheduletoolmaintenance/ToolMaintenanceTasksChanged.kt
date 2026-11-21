package com.toolshed.scheduletoolmaintenance

import com.toolshed.axon.ToolId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class ToolMaintenanceTasksChanged(
    @field:EventTag(key = "toolId")
    val toolId: ToolId,
    val tasks: List<String>,
    val notes: String?,
    val changedBy: String,
    val changedAt: Instant,
)
