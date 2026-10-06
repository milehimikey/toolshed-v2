package com.toolshed.extendloan

import com.toolshed.axon.LoanId
import java.time.Instant
import java.time.LocalDate

data class LoanExtended(
    val loanId: LoanId,
    val fromDueDate: LocalDate,
    val dueDate: LocalDate,
    val extendedAt: Instant,
)
