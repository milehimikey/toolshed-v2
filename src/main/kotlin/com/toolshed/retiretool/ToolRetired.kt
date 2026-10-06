package com.toolshed.retiretool

import com.toolshed.axon.ToolId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class ToolRetired(
    @field:EventTag(key = "toolId")
    val toolId: ToolId,
    val retiredAt: Instant,
)
