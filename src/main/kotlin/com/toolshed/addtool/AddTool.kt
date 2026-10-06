package com.toolshed.addtool

import com.toolshed.axon.ToolId
import org.axonframework.modelling.annotation.TargetEntityId
import java.math.BigDecimal

/** Absent (null) name or period are refused with name-missing or period-missing, not bad input. */
data class AddTool(
    @TargetEntityId val toolId: ToolId,
    val name: String?,
    val description: String?,
    val defaultCheckoutPeriodDays: BigDecimal?,
)
