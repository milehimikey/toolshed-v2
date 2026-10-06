package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.LoanId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class OverdueNoticeFailed(
    @field:EventTag(key = "loanId")
    val loanId: LoanId,
    val reason: String,
    val failedAt: Instant,
)
