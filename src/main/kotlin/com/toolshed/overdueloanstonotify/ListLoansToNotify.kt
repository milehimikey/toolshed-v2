package com.toolshed.overdueloanstonotify

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import java.time.LocalDate

/** The Overdue Notice Gateway asking for its list. It takes nothing and is internal: no caller identity applies. */
data object ListLoansToNotify

/** One overdue loan whose borrower hasn't been sent a notice, carrying everything the notice needs (INV-OLN-6). */
data class LoanToNotifyRow(
    val loanId: LoanId,
    val toolId: ToolId,
    val neighborId: NeighborId,
    val dueDate: LocalDate,
    val toolName: String,
    val borrowerName: String,
    val borrowerEmail: String,
)
