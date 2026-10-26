package com.toolshed.myloans

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import java.time.Instant
import java.time.LocalDate

/** One open loan on a neighbor's My Loans screen. */
data class MyLoanRow(
    val loanId: LoanId,
    val neighborId: NeighborId,
    val toolId: ToolId,
    val toolName: String,
    val checkedOutAt: Instant,
    val dueDate: LocalDate,
    val overdue: Boolean,
    val overdueNotice: String,
    val holdWaiting: Boolean,
    val toolRetired: Boolean,
    val canExtend: Boolean,
)

/** The query's answer: every open loan whose borrower is the neighbor asked for, in INV-MYL-13 order. */
data class MyLoans(
    val loans: List<MyLoanRow>,
)

/** My Loans for one neighbor, asked by that neighbor's id. */
data class GetMyLoans(
    val neighborId: NeighborId,
)
