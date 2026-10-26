package com.toolshed.myloans

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The in-memory read model behind My Loans (Amendment 5). Every apply is idempotent (INV-MYL-11): a replayed event
 * leaves the view as it was after the first. A tool's name, a loan, a tool's retirement and a tool's waiting holds
 * are each kept independently and combined only when a row is read, because none of them need to change together
 * in one atomic step the way Tool Inventory's loan-and-retirement pair does.
 */
@Repository
class MyLoansRepository {
    private data class Loan(
        val loanId: LoanId,
        val neighborId: NeighborId,
        val toolId: ToolId,
        val checkedOutAt: Instant,
        val dueDate: LocalDate,
        val overdueNotice: String = "none",
        val sequence: Long,
    )

    private data class Hold(
        val toolId: ToolId,
        val holder: NeighborId,
    )

    private val toolNames = ConcurrentHashMap<ToolId, String>()
    private val loans = ConcurrentHashMap<LoanId, Loan>()
    private val holds = ConcurrentHashMap<HoldId, Hold>()
    private val retiredTools = ConcurrentHashMap.newKeySet<ToolId>()
    private val sequence = AtomicLong()

    fun addTool(
        toolId: ToolId,
        name: String,
    ) {
        toolNames.putIfAbsent(toolId, name)
    }

    /** A checkout opens the loan and, if the borrower was waiting on this tool, ends that hold (hold rules). */
    fun checkOut(
        loanId: LoanId,
        toolId: ToolId,
        neighborId: NeighborId,
        checkedOutAt: Instant,
        dueDate: LocalDate,
    ) {
        val new = Loan(loanId, neighborId, toolId, checkedOutAt, dueDate, sequence = sequence.getAndIncrement())
        if (loans.putIfAbsent(loanId, new) == null) {
            holds.entries.removeIf { (_, hold) -> hold.toolId == toolId && hold.holder == neighborId }
        }
    }

    fun extend(
        loanId: LoanId,
        dueDate: LocalDate,
    ) {
        loans.computeIfPresent(loanId) { _, loan -> loan.copy(dueDate = dueDate) }
    }

    fun noticeSent(loanId: LoanId) {
        loans.computeIfPresent(loanId) { _, loan -> loan.copy(overdueNotice = "sent") }
    }

    fun noticeFailed(loanId: LoanId) {
        loans.computeIfPresent(loanId) { _, loan -> loan.copy(overdueNotice = "undelivered") }
    }

    /** Tool Returned: the loan drops off (INV-MYL-6). It doesn't end any hold. */
    fun endLoan(loanId: LoanId) {
        loans.remove(loanId)
    }

    /** Tool Lost: the loan drops off, and any hold waiting on the tool ends with it (hold rules). */
    fun recordLost(
        loanId: LoanId,
        toolId: ToolId,
    ) {
        loans.remove(loanId)
        endHoldsOnTool(toolId)
    }

    fun placeHold(
        holdId: HoldId,
        toolId: ToolId,
        neighborId: NeighborId,
    ) {
        holds.putIfAbsent(holdId, Hold(toolId, neighborId))
    }

    fun endHold(holdId: HoldId) {
        holds.remove(holdId)
    }

    /** Tool Retired: the tool is marked retired, and any hold waiting on it ends (hold rules). */
    fun retire(toolId: ToolId) {
        retiredTools.add(toolId)
        endHoldsOnTool(toolId)
    }

    /** Neighbor Account Closed: every hold held by that neighbor ends, on whatever tool it was on. */
    fun closeAccount(neighborId: NeighborId) {
        holds.entries.removeIf { (_, hold) -> hold.holder == neighborId }
    }

    private fun endHoldsOnTool(toolId: ToolId) {
        holds.entries.removeIf { (_, hold) -> hold.toolId == toolId }
    }

    /** The borrower's open loans as My Loans shows them, in INV-MYL-13 order, with overdue worked out against [today]. */
    fun rows(
        neighborId: NeighborId,
        today: LocalDate,
    ): List<MyLoanRow> =
        loans.values
            .filter { it.neighborId == neighborId }
            .sortedWith(compareBy({ toolNames[it.toolId]?.lowercase() ?: "" }, { it.checkedOutAt }, { it.sequence }))
            .map { loan ->
                val overdue = today.isAfter(loan.dueDate)
                val retired = retiredTools.contains(loan.toolId)
                val holdWaiting = holds.values.any { it.toolId == loan.toolId }
                MyLoanRow(
                    loanId = loan.loanId,
                    neighborId = loan.neighborId,
                    toolId = loan.toolId,
                    toolName = toolNames[loan.toolId] ?: "",
                    checkedOutAt = loan.checkedOutAt,
                    dueDate = loan.dueDate,
                    overdue = overdue,
                    overdueNotice = loan.overdueNotice,
                    holdWaiting = holdWaiting,
                    toolRetired = retired,
                    canExtend = !overdue && !holdWaiting && !retired,
                )
            }
}
