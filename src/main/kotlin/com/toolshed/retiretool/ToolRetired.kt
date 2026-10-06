package com.toolshed.retiretool

import com.toolshed.axon.ToolId
import java.time.Instant

data class ToolRetired(
    val toolId: ToolId,
    val retiredAt: Instant,
)
