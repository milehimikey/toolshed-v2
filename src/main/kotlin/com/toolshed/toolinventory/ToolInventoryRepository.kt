package com.toolshed.toolinventory

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

/**
 * The in-memory read model behind the Tool Inventory view (Amendment 5). Every apply is idempotent (INV-TINV-8):
 * a replayed event leaves the view as it was after the first. Mutable maps, because the shed's history is long.
 */
@Component
class ToolInventoryRepository {
    private class Tool(
        val toolId: ToolId,
        val name: String,
        val description: String?,
        val defaultCheckoutPeriodDays: Int,
        val addedAt: Instant,
        val sequence: Long,
    )

    private class Loan(
        val loanId: LoanId,
        val toolId: ToolId,
        val borrower: NeighborId,
        var dueDate: LocalDate,
        var ended: Boolean,
    )

    private val tools = linkedMapOf<ToolId, Tool>()
    private val retired = mutableSetOf<ToolId>()
    private val loans = mutableMapOf<LoanId, Loan>()
    private val names = mutableMapOf<NeighborId, String>()
    private var sequence = 0L

    fun addTool(
        toolId: ToolId,
        name: String,
        description: String?,
        defaultCheckoutPeriodDays: Int,
        addedAt: Instant,
    ) {
        if (toolId in tools) return
        tools[toolId] = Tool(toolId, name, description, defaultCheckoutPeriodDays, addedAt, sequence++)
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
        if (loanId in loans) return
        loans[loanId] = Loan(loanId, toolId, borrower, dueDate, ended = false)
    }

    fun extend(
        loanId: LoanId,
        dueDate: LocalDate,
    ) {
        loans[loanId]?.let { if (!it.ended) it.dueDate = dueDate }
    }

    fun endLoan(loanId: LoanId) {
        loans[loanId]?.ended = true
    }

    fun retire(toolId: ToolId) {
        retired.add(toolId)
    }

    /** The screen's rows, in INV-TINV-10 order, with overdue worked out against [today] in the shed's zone (INV-TINV-9). */
    fun rows(today: LocalDate): List<ToolInventoryRow> {
        val currentLoan = loans.values.filter { !it.ended }.associateBy { it.toolId }
        return tools.values
            .map { tool ->
                val loan = currentLoan[tool.toolId]
                val isRetired = tool.toolId in retired
                Triple(tool, loan, isRetired)
            }
            // INV-TINV-2: a retired tool drops off once it is back in the shed or recorded lost.
            .filter { (_, loan, isRetired) -> loan != null || !isRetired }
            .sortedWith(
                compareBy<Triple<Tool, Loan?, Boolean>>({ it.first.name.lowercase() }, { it.first.addedAt }, { it.first.sequence }),
            ).map { (tool, loan, isRetired) ->
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
                    retired = isRetired,
                    canRetire = !isRetired,
                    canMarkLost = loan != null,
                    overdue = loan != null && today.isAfter(loan.dueDate),
                )
            }
    }
}
