package com.toolshed.returntool

import com.toolshed.axon.LoanId
import com.toolshed.axon.ToolId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant
import java.time.LocalDate

data class ToolReturned(
    @field:EventTag(key = "loanId")
    val loanId: LoanId,
    @field:EventTag(key = "toolId")
    val toolId: ToolId,
    val returnedAt: Instant,
    val returnedOn: LocalDate,
    val late: Boolean,
)
