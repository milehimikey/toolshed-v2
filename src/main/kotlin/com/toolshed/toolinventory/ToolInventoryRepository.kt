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
 * A tool that drops off (INV-TINV-2) stays in the map flagged as dropped, so a replayed Tool Added can never
 * bring it back (INV-TINV-1). Overdue is worked out at read time from the due date in force (INV-TINV-9).
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
        val dropped: Boolean = false,
    )

    private val tools = ConcurrentHashMap<ToolId, Tool>()
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
        tools.computeIfAbsent(toolId) { Tool(toolId, name, description, defaultCheckoutPeriodDays, addedAt, sequence.getAndIncrement()) }
    }

    /** Supplies the borrower names; changes no entry (INV-TINV-3). */
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
            tools.computeIfPresent(toolId) { _, tool ->
                if (tool.dropped) tool else tool.copy(loan = OpenLoan(loanId, borrower, dueDate))
            }
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

    /** The loan ends; a tool retired while out drops off now (INV-TINV-2). */
    fun endLoan(loanId: LoanId) {
        val toolId = toolOfLoan[loanId] ?: return
        tools.computeIfPresent(toolId) { _, tool ->
            if (tool.loan?.loanId == loanId) tool.copy(loan = null, dropped = tool.retired) else tool
        }
    }

    /** In the shed it drops off at once; out on loan it stays, marked retired (INV-TINV-2). */
    fun retire(toolId: ToolId) {
        tools.computeIfPresent(toolId) { _, tool ->
            when {
                tool.dropped || tool.retired -> tool
                tool.loan == null -> tool.copy(retired = true, dropped = true)
                else -> tool.copy(retired = true)
            }
        }
    }

    /** A tool recorded lost is retired by that, so it drops off whatever its state (INV-TINV-2). */
    fun recordLost(
        loanId: LoanId,
        toolId: ToolId,
    ) {
        tools.computeIfPresent(toolId) { _, tool ->
            val loan = tool.loan
            if (!tool.dropped && (loan == null || loan.loanId == loanId)) tool.copy(loan = null, retired = true, dropped = true) else tool
        }
    }

    /** The screen's rows in INV-TINV-10 order, with overdue worked out against [today] in the shed's zone. */
    fun rows(today: LocalDate): List<ToolInventoryRow> =
        tools.values
            .filterNot { it.dropped }
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
                    borrowerName = loan?.let { neighborNames[it.borrowerId].orEmpty() },
                    dueDate = loan?.dueDate,
                    retired = tool.retired,
                    canRetire = !tool.retired,
                    overdue = loan != null && today.isAfter(loan.dueDate),
                    canMarkLost = loan != null,
                )
            }
}
