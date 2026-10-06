package com.toolshed.checkouttool

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import java.time.Instant
import java.time.LocalDate

data class ToolCheckedOut(
    val loanId: LoanId,
    val toolId: ToolId,
    val neighborId: NeighborId,
    val loanPeriodDays: Int,
    val dueDate: LocalDate,
    val checkedOutAt: Instant,
)
