package com.toolshed.recordtoollost

import com.toolshed.axon.LoanId
import com.toolshed.axon.ToolId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class ToolLost(
    @field:EventTag(key = "loanId")
    val loanId: LoanId,
    @field:EventTag(key = "toolId")
    val toolId: ToolId,
    val lostAt: Instant,
)
