package com.toolshed.sendoverduenotice

import com.toolshed.axon.LoanId
import java.time.Instant
import java.time.LocalDate

data class OverdueNoticeSent(
    val loanId: LoanId,
    val sentTo: String,
    val borrowerName: String,
    val toolName: String,
    val dueDate: LocalDate,
    val sentAt: Instant,
)
