package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.LoanId
import java.time.Instant

data class OverdueNoticeFailed(
    val loanId: LoanId,
    val reason: String,
    val failedAt: Instant,
)
