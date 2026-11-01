package com.toolshed.toolinventory

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import java.time.Instant
import java.time.LocalDate

/** One row of the Tool Inventory view. Loan fields are present only while the tool is on loan; never the borrower's email. */
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
    val overdue: Boolean,
    val canMarkLost: Boolean,
)

/** The query's answer: every listed tool, ordered for the screen (INV-TINV-10). No paging. */
data class ToolInventory(
    val tools: List<ToolInventoryRow>,
)

/** The query the Tool Inventory screen asks. Takes nothing. */
object ListToolInventory

/** Identity checks that run before the query is asked (Amendment 6): INV-TINV-11, then INV-TINV-12. */
fun Caller.requireToolInventoryReader() {
    requireShedManager(
        notSignedIn = Refusal("INV-TINV-11", "Please sign in."),
        notShedManager = Refusal("INV-TINV-12", "Only the shed manager can see the tool inventory."),
    )
}
