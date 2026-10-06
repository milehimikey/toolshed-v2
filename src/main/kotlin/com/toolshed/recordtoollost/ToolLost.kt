package com.toolshed.recordtoollost

import com.toolshed.axon.LoanId
import com.toolshed.axon.ToolId
import java.time.Instant

data class ToolLost(
    val loanId: LoanId,
    val toolId: ToolId,
    val lostAt: Instant,
)
