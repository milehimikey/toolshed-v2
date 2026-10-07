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
 * The in-memory read model behind the Tool Inventory view (Amendment 5). Every apply is idempotent (INV-TINV-8): a
 * replayed event leaves the view as it was after the first. Projection threads write while REST reads, so each tool is
 * one immutable record replaced in one atomic step: its open loan and its retirement change together, so a reader never
 * sees half of an event (Tool Lost ends the loan and retires the tool in one step).
 */
@Component
class ToolInventoryRepository {
    private data class OpenLoan(
        val loanId: LoanId,
        val borrower: NeighborId,
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
    private val toolOfLoan = ConcurrentHashMap<LoanId, ToolId>()
    private val names = ConcurrentHashMap<NeighborId, String>()
    private val sequence = AtomicLong()

    fun addTool(
        toolId: ToolId,
        name: String,
        description: String?,
        defaultCheckoutPeriodDays: Int,
        addedAt: Instant,
    ) {
        tools.computeIfAbsent(toolId) { Tool(toolId, name, description, defaultCheckoutPeriodDays, addedAt, sequence.getAndIncrement()) }
    }

    fun registerNeighbor(
        neighborId: NeighborId,
        name: String,
    ) {
        names.putIfAbsent(neighborId, name)
    }

    fun checkOut(
        loanId: LoanId,
        toolId: ToolId,
        borrower: NeighborId,
        dueDate: LocalDate,
    ) {
        // A loan is applied once: a replayed checkout never reopens a loan that has already ended.
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

    fun endLoan(loanId: LoanId) {
        val toolId = toolOfLoan[loanId] ?: return
        tools.computeIfPresent(toolId) { _, tool -> if (tool.loan?.loanId == loanId) tool.copy(loan = null) else tool }
    }

    fun retire(toolId: ToolId) {
        tools.computeIfPresent(toolId) { _, tool -> tool.copy(retired = true) }
    }

    /** Tool Lost: the loan ends and the tool is retired in one atomic step, so no reader sees one without the other. */
    fun recordLost(
        loanId: LoanId,
        toolId: ToolId,
    ) {
        tools.computeIfPresent(toolId) { _, tool ->
            val loan = tool.loan
            tool.copy(loan = if (loan?.loanId == loanId) null else loan, retired = true)
        }
    }

    /** The screen's rows, in INV-TINV-10 order, with overdue worked out against [today] in the shed's zone (INV-TINV-9). */
    fun rows(today: LocalDate): List<ToolInventoryRow> =
        tools.values
            // INV-TINV-2: a retired tool drops off once it is back in the shed or recorded lost.
            .filter { it.loan != null || !it.retired }
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
                    borrowerId = loan?.borrower,
                    borrowerName = loan?.let { names[it.borrower] },
                    dueDate = loan?.dueDate,
                    retired = tool.retired,
                    canRetire = !tool.retired,
                    canMarkLost = loan != null,
                    overdue = loan != null && today.isAfter(loan.dueDate),
                )
            }
}
