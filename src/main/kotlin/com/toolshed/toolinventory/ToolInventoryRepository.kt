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
 * A tool that drops off (INV-TINV-2) is remembered as dropped, so a replayed Tool Added never brings it back and
 * a later event for it changes nothing. Overdue is worked out when the rows are read (INV-TINV-9), never stored.
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
    private val dropped = ConcurrentHashMap.newKeySet<ToolId>()
    private val neighborNames = ConcurrentHashMap<NeighborId, String>()
    private val toolOfLoan = ConcurrentHashMap<LoanId, ToolId>()
    private val sequence = AtomicLong()

    fun addTool(
        toolId: ToolId,
        name: String,
        description: String?,
        defaultCheckoutPeriodDays: Int,
        addedAt: Instant,
    ) {
        if (toolId in dropped) return
        tools.computeIfAbsent(toolId) { Tool(toolId, name, description, defaultCheckoutPeriodDays, addedAt, sequence.getAndIncrement()) }
    }

    /** Supplies the borrower names shown on tools that are out; changes no entry (INV-TINV-3). */
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

    /** Back in the shed: a retired tool drops off, any other is simply no longer out. */
    fun endLoan(loanId: LoanId) {
        val toolId = toolOfLoan[loanId] ?: return
        tools.computeIfPresent(toolId) { _, tool ->
            when {
                tool.loan?.loanId != loanId -> tool
                tool.retired -> drop(toolId)
                else -> tool.copy(loan = null)
            }
        }
    }

    /** Retired in the shed: drops off at once. Retired while out: stays listed, marked retired, until it ends. */
    fun retire(toolId: ToolId) {
        tools.computeIfPresent(toolId) { _, tool -> if (tool.loan == null) drop(toolId) else tool.copy(retired = true) }
    }

    fun recordLost(toolId: ToolId) {
        tools.computeIfPresent(toolId) { _, _ -> drop(toolId) }
    }

    private fun drop(toolId: ToolId): Tool? {
        dropped.add(toolId)
        return null
    }

    /** The screen's rows in INV-TINV-10 order; overdue is judged against [today] in the shed's zone (INV-TINV-9). */
    fun rows(today: LocalDate): List<ToolInventoryRow> =
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
                    overdue = loan != null && today.isAfter(loan.dueDate),
                    canMarkLost = loan != null,
                )
            }
}
