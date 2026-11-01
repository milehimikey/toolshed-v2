package com.toolshed.toolcatalog

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The in-memory read model behind the Tool Catalog view (Amendment 5). Every apply is idempotent
 * (INV-TCAT-12): a replayed event leaves the entry as it was after the first. A tool's status is
 * worked out at read time from its current facts (loan, holds), never stored, so the order events
 * arrive in doesn't matter. Retiring or recording a tool lost removes its entry outright
 * (INV-TCAT-8, INV-TCAT-10) and remembers the tool as dropped, so a later event for it — even a
 * Tool Added delivered again out of order — is a no-op, never re-creating it (INV-TCAT-12).
 */
@Component
class ToolCatalogRepository {
    private data class Hold(
        val holdId: HoldId,
        val neighborId: NeighborId,
        val fulfilled: Boolean,
    )

    private data class OpenLoan(
        val loanId: LoanId,
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
        val holds: List<Hold> = emptyList(),
    )

    private val tools = ConcurrentHashMap<ToolId, Tool>()
    private val dropped = ConcurrentHashMap.newKeySet<ToolId>()
    private val toolOfLoan = ConcurrentHashMap<LoanId, ToolId>()
    private val toolOfHold = ConcurrentHashMap<HoldId, ToolId>()
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

    /** The borrower's own hold on this tool, waiting or set aside, ends the moment they check it out. */
    fun checkOut(
        loanId: LoanId,
        toolId: ToolId,
        borrower: NeighborId,
        dueDate: LocalDate,
    ) {
        if (toolOfLoan.putIfAbsent(loanId, toolId) == null) {
            tools.computeIfPresent(toolId) { _, tool ->
                tool.copy(loan = OpenLoan(loanId, dueDate), holds = tool.holds.filterNot { it.neighborId == borrower })
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

    fun endLoan(loanId: LoanId) {
        val toolId = toolOfLoan[loanId] ?: return
        tools.computeIfPresent(toolId) { _, tool -> if (tool.loan?.loanId == loanId) tool.copy(loan = null) else tool }
    }

    fun placeHold(
        holdId: HoldId,
        toolId: ToolId,
        neighborId: NeighborId,
    ) {
        if (toolOfHold.putIfAbsent(holdId, toolId) == null) {
            tools.computeIfPresent(toolId) { _, tool -> tool.copy(holds = tool.holds + Hold(holdId, neighborId, fulfilled = false)) }
        }
    }

    fun cancelHold(holdId: HoldId) = endHold(holdId)

    fun expireHold(holdId: HoldId) = endHold(holdId)

    private fun endHold(holdId: HoldId) {
        val toolId = toolOfHold[holdId] ?: return
        tools.computeIfPresent(toolId) { _, tool -> tool.copy(holds = tool.holds.filterNot { it.holdId == holdId }) }
    }

    fun fulfillHold(
        holdId: HoldId,
        toolId: ToolId,
    ) {
        tools.computeIfPresent(toolId) { _, tool ->
            tool.copy(holds = tool.holds.map { if (it.holdId == holdId) it.copy(fulfilled = true) else it })
        }
    }

    fun retire(toolId: ToolId) {
        drop(toolId)
    }

    fun recordLost(toolId: ToolId) {
        drop(toolId)
    }

    /** A dropped tool never comes back, whatever arrives for it later (INV-TCAT-8, INV-TCAT-10, INV-TCAT-12). */
    private fun drop(toolId: ToolId) {
        dropped.add(toolId)
        tools.remove(toolId)
    }

    /** Ends every hold the closed account holds, on every tool, wherever they are waiting (INV-TCAT-6). */
    fun closeAccount(neighborId: NeighborId) {
        tools.keys.forEach { toolId ->
            tools.computeIfPresent(toolId) { _, tool ->
                if (tool.holds.any { it.neighborId == neighborId }) {
                    tool.copy(holds = tool.holds.filterNot { it.neighborId == neighborId })
                } else {
                    tool
                }
            }
        }
    }

    /** The screen's rows, in INV-TCAT-13 order, with status, due date, holds waiting and the two offers derived here. */
    fun rows(): List<ToolCatalogRow> =
        tools.values
            .sortedWith(compareBy({ it.name.lowercase() }, { it.addedAt }, { it.sequence }))
            .map { tool ->
                val waiting = tool.holds.count { !it.fulfilled }
                val status =
                    when {
                        tool.loan != null -> ToolCatalogStatus.CHECKED_OUT
                        tool.holds.any { it.fulfilled } -> ToolCatalogStatus.SET_ASIDE
                        tool.holds.isNotEmpty() -> ToolCatalogStatus.RESERVED
                        else -> ToolCatalogStatus.AVAILABLE
                    }
                ToolCatalogRow(
                    toolId = tool.toolId,
                    name = tool.name,
                    description = tool.description,
                    defaultCheckoutPeriodDays = tool.defaultCheckoutPeriodDays,
                    addedAt = tool.addedAt,
                    status = status,
                    dueDate = tool.loan?.dueDate,
                    holdsWaiting = waiting,
                    canCheckOut = status != ToolCatalogStatus.CHECKED_OUT,
                    canPlaceHold = status != ToolCatalogStatus.AVAILABLE,
                )
            }
}
