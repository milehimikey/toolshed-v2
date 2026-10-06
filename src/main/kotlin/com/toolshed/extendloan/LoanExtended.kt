package com.toolshed.extendloan

import com.toolshed.axon.LoanId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant
import java.time.LocalDate

data class LoanExtended(
    @field:EventTag(key = "loanId")
    val loanId: LoanId,
    val fromDueDate: LocalDate,
    val dueDate: LocalDate,
    val extendedAt: Instant,
)
