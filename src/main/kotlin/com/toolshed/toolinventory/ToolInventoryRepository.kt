package com.toolshed.toolinventory

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The in-memory read model behind the Tool Inventory view (Amendment 5). Every apply is idempotent (INV-TINV-8).
 * A tool leaves the list when it is retired in the shed, or when a retired tool is returned, or when it is
 * recorded lost (INV-TINV-2); a tool that has left is remembered, so a replayed Tool Added never brings it back.
 * Borrower names are looked up when the rows are read, so Neighbor Registered may arrive in any order
 * relative to the checkout. Overdue is worked out by the query handler at read time (INV-TINV-9).
 */
@Component
class ToolInventoryRepository {
    private data class OpenLoan(
        val loanId: LoanId,
        val borrowerId: NeighborId,
        val dueDate: LocalDate,
    )

    private data class Tool(
        val toolId: ToolId,
        val name: String,
        val description: String?,
        val defaultCheckoutPeriodDays: Int,
        val addedAt: Instant,
        val sequence: Long,
        val loan: OpenLoan? = null,
        val retired: Boolean = false,
    )

    private val tools = ConcurrentHashMap<ToolId, Tool>()
    private val gone = ConcurrentHashMap.newKeySet<ToolId>()
    private val toolOfLoan = ConcurrentHashMap<LoanId, ToolId>()
    private val neighborNames = ConcurrentHashMap<NeighborId, String>()
    private val sequence = AtomicLong()

    fun addTool(
        toolId: ToolId,
        name: String,
        description: String?,
        defaultCheckoutPeriodDays: Int,
        addedAt: Instant,
    ) {
        if (toolId in gone) return
        tools.computeIfAbsent(toolId) { Tool(toolId, name, description, defaultCheckoutPeriodDays, addedAt, sequence.getAndIncrement()) }
    }

    fun registerNeighbor(
        neighborId: NeighborId,
        name: String,
    ) {
        neighborNames.putIfAbsent(neighborId, name)
    }

    fun checkOut(
        loanId: LoanId,
        toolId: ToolId,
        borrower: NeighborId,
        dueDate: LocalDate,
    ) {
        if (toolOfLoan.putIfAbsent(loanId, toolId) == null) {
            tools.computeIfPresent(toolId) { _, tool -> tool.copy(loan = OpenLoan(loanId, borrower, dueDate)) }
        }
    }

    fun extend(
        loanId: LoanId,
        dueDate: LocalDate,
    ) {
        val toolId = toolOfLoan[loanId] ?: return
        tools.computeIfPresent(toolId) { _, tool ->
            val loan = tool.loan
            if (loan?.loanId == loanId) tool.copy(loan = loan.copy(dueDate = dueDate)) else tool
        }
    }

    /** The loan ends; a retired tool leaves the list with it (INV-TINV-2). */
    fun endLoan(loanId: LoanId) {
        val toolId = toolOfLoan[loanId] ?: return
        tools.computeIfPresent(toolId) { _, tool ->
            when {
                tool.loan?.loanId != loanId -> tool
                tool.retired -> {
                    gone.add(toolId)
                    null
                }
                else -> tool.copy(loan = null)
            }
        }
    }

    /** Retired in the shed drops off at once; retired while out stays, marked retired (INV-TINV-2). */
    fun retire(toolId: ToolId) {
        tools.computeIfPresent(toolId) { _, tool ->
            if (tool.loan == null) {
                gone.add(toolId)
                null
            } else {
                tool.copy(retired = true)
            }
        }
    }

    fun recordLost(toolId: ToolId) {
        gone.add(toolId)
        tools.remove(toolId)
    }

    /** Rows in INV-TINV-10 order; [isOverdue] decides overdue from the due date when the inventory is read. */
    fun rows(isOverdue: (LocalDate) -> Boolean): List<ToolInventoryRow> =
        tools.values
            .sortedWith(compareBy({ it.name.lowercase() }, { it.addedAt }, { it.sequence }))
            .map { tool ->
                val loan = tool.loan
                ToolInventoryRow(
                    toolId = tool.toolId,
                    name = tool.name,
                    description = tool.description,
                    defaultCheckoutPeriodDays = tool.defaultCheckoutPeriodDays,
                    addedAt = tool.addedAt,
                    onLoan = loan != null,
                    loanId = loan?.loanId,
                    borrowerId = loan?.borrowerId,
                    borrowerName = loan?.let { neighborNames[it.borrowerId] },
                    dueDate = loan?.dueDate,
                    retired = tool.retired,
                    canRetire = !tool.retired,
                    overdue = loan != null && isOverdue(loan.dueDate),
                    canMarkLost = loan != null,
                )
            }
}
