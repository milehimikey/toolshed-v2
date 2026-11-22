package com.toolshed.checkouttool

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant
import java.time.LocalDate

data class ToolCheckedOut(
    @field:EventTag(key = "loanId")
    val loanId: LoanId,
    @field:EventTag(key = "toolId")
    val toolId: ToolId,
    @field:EventTag(key = "neighborId")
    val neighborId: NeighborId,
    val loanPeriodDays: Int,
    val dueDate: LocalDate,
    val checkedOutAt: Instant,
)
