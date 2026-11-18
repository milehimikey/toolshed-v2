package com.toolshed.overdueloanstonotify

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate

/**
 * Overdue Loans To Notify's own storage (Amendment 5): in-memory, rebuilt from the event stream on restart. Every
 * mutation leaves the store as it was after the first application when repeated (INV-OLN-9).
 */
interface OverdueLoansToNotifyRepository {
    fun toolAdded(
        toolId: ToolId,
        name: String,
    )

    fun neighborRegistered(
        neighborId: NeighborId,
        name: String,
        email: String,
    )

    fun emailChanged(
        neighborId: NeighborId,
        email: String,
    )

    fun checkedOut(
        loanId: LoanId,
        toolId: ToolId,
        neighborId: NeighborId,
        dueDate: LocalDate,
    )

    fun extended(
        loanId: LoanId,
        dueDate: LocalDate,
    )

    /** Notice sent, tool returned or tool lost: the loan leaves and never comes back (INV-OLN-3, INV-OLN-4, INV-OLN-8). */
    fun end(loanId: LoanId)

    /** Every row whose due date is before [today] (INV-OLN-1), with names and the current email filled in (INV-OLN-6). */
    fun overdue(today: LocalDate): List<OverdueLoanToNotifyRow>
}

class InMemoryOverdueLoansToNotifyRepository : OverdueLoansToNotifyRepository {
    private data class Loan(
        val toolId: ToolId,
        val neighborId: NeighborId,
        val dueDate: LocalDate,
    )

    private data class Neighbor(
        val name: String,
        val email: String,
    )

    private val loans = LinkedHashMap<LoanId, Loan>()
    private val toolNames = HashMap<ToolId, String>()
    private val neighbors = HashMap<NeighborId, Neighbor>()

    /** Nothing puts a row back, so a replayed Tool Checked Out for a loan that has left must not revive it. */
    private val ended = HashSet<LoanId>()

    @Synchronized
    override fun toolAdded(
        toolId: ToolId,
        name: String,
    ) {
        toolNames.putIfAbsent(toolId, name)
    }

    /** A replayed registration must not undo a later email change, so the first one wins. */
    @Synchronized
    override fun neighborRegistered(
        neighborId: NeighborId,
        name: String,
        email: String,
    ) {
        neighbors.putIfAbsent(neighborId, Neighbor(name, email))
    }

    @Synchronized
    override fun emailChanged(
        neighborId: NeighborId,
        email: String,
    ) {
        val known = neighbors[neighborId] ?: return
        neighbors[neighborId] = known.copy(email = email)
    }

    @Synchronized
    override fun checkedOut(
        loanId: LoanId,
        toolId: ToolId,
        neighborId: NeighborId,
        dueDate: LocalDate,
    ) {
        if (loanId in ended || loanId in loans) return
        loans[loanId] = Loan(toolId, neighborId, dueDate)
    }

    @Synchronized
    override fun extended(
        loanId: LoanId,
        dueDate: LocalDate,
    ) {
        val loan = loans[loanId] ?: return
        loans[loanId] = loan.copy(dueDate = dueDate)
    }

    @Synchronized
    override fun end(loanId: LoanId) {
        loans.remove(loanId)
        ended.add(loanId)
    }

    @Synchronized
    override fun overdue(today: LocalDate): List<OverdueLoanToNotifyRow> =
        loans.entries.mapNotNull { (loanId, loan) ->
            val toolName = toolNames[loan.toolId]
            val borrower = neighbors[loan.neighborId]
            if (!today.isAfter(loan.dueDate) || toolName == null || borrower == null) {
                null
            } else {
                OverdueLoanToNotifyRow(loanId, loan.toolId, loan.neighborId, loan.dueDate, toolName, borrower.name, borrower.email)
            }
        }
}

@Configuration
class OverdueLoansToNotifyRepositoryConfiguration {
    @Bean
    fun overdueLoansToNotifyRepository(): OverdueLoansToNotifyRepository = InMemoryOverdueLoansToNotifyRepository()
}
