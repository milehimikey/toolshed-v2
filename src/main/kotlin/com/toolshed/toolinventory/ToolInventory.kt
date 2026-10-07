package com.toolshed.toolinventory

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import java.time.Instant
import java.time.LocalDate

/** One row of the Tool Inventory view: a listed tool with everything the shed manager's screen shows. */
data class ToolInventoryRow(
    val toolId: ToolId,
    val name: String,
    val description: String?,
    val defaultCheckoutPeriodDays: Int,
    val addedAt: Instant,
    val onLoan: Boolean,
    val loanId: LoanId?,
    val borrowerId: NeighborId?,
    val borrowerName: String?,
    val dueDate: LocalDate?,
    val retired: Boolean,
    val canRetire: Boolean,
    val canMarkLost: Boolean,
    val overdue: Boolean,
)

/** The query's answer: every listed tool, ordered for the screen (INV-TINV-10). */
data class ToolInventory(
    val tools: List<ToolInventoryRow>,
)

/** The query the shed manager's screen asks. Takes nothing; there is no paging. */
object ListToolInventory
