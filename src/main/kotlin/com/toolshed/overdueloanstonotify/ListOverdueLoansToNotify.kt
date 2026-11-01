package com.toolshed.overdueloanstonotify

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import java.time.LocalDate

/** The overdue notice gateway asking for its to-do list. It takes nothing and is internal: no caller identity applies. */
data object ListOverdueLoansToNotify

/** One overdue loan still waiting for its notice, carrying everything the email needs (INV-OLN-6). */
data class OverdueLoanToNotifyRow(
    val loanId: LoanId,
    val toolId: ToolId,
    val neighborId: NeighborId,
    val dueDate: LocalDate,
    val toolName: String,
    val borrowerName: String,
    val borrowerEmail: String,
)
