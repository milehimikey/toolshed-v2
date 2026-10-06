package com.toolshed.returntool

import com.toolshed.axon.LoanId
import com.toolshed.axon.ToolId
import java.time.Instant
import java.time.LocalDate

data class ToolReturned(
    val loanId: LoanId,
    val toolId: ToolId,
    val returnedAt: Instant,
    val returnedOn: LocalDate,
    val late: Boolean,
)
