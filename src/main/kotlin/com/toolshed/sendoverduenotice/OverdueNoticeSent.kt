package com.toolshed.sendoverduenotice

import com.toolshed.axon.LoanId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant
import java.time.LocalDate

data class OverdueNoticeSent(
    @field:EventTag(key = "loanId")
    val loanId: LoanId,
    val sentTo: String,
    val borrowerName: String,
    val toolName: String,
    val dueDate: LocalDate,
    val sentAt: Instant,
)
