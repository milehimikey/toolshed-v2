package com.toolshed.axon

import java.time.Instant
import java.time.LocalDate

data class LateReturn(
    val loanId: LoanId,
    val toolName: String,
    val dueDate: LocalDate,
    val returnedOn: LocalDate,
    val flaggedAt: Instant,
)
