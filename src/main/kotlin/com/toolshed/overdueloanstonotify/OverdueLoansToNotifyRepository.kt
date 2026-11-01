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

    /** The loan's notice went out, or the tool came back or was lost; it never comes back (INV-OLN-3, INV-OLN-4, INV-OLN-8). */
    fun end(loanId: LoanId)

    /** Every row whose due date is before [today] (INV-OLN-1). */
    fun overdue(today: LocalDate): List<OverdueLoanToNotifyRow>
}

private class OpenLoan(
    val loanId: LoanId,
    val toolId: ToolId,
    val neighborId: NeighborId,
    var dueDate: LocalDate,
)

private class Borrower(
    val name: String,
    var email: String,
)

class InMemoryOverdueLoansToNotifyRepository : OverdueLoansToNotifyRepository {
    private val tools = HashMap<ToolId, String>()
    private val neighbors = HashMap<NeighborId, Borrower>()
    private val loans = LinkedHashMap<LoanId, OpenLoan>()

    /** Nothing puts a row back, so a replayed Tool Checked Out for a loan that has left must not revive it. */
    private val ended = HashSet<LoanId>()

    @Synchronized
    override fun toolAdded(
        toolId: ToolId,
        name: String,
    ) {
        tools.putIfAbsent(toolId, name)
    }

    @Synchronized
    override fun neighborRegistered(
        neighborId: NeighborId,
        name: String,
        email: String,
    ) {
        neighbors.putIfAbsent(neighborId, Borrower(name, email))
    }

    @Synchronized
    override fun emailChanged(
        neighborId: NeighborId,
        email: String,
    ) {
        neighbors[neighborId]?.email = email
    }

    @Synchronized
    override fun checkedOut(
        loanId: LoanId,
        toolId: ToolId,
        neighborId: NeighborId,
        dueDate: LocalDate,
    ) {
        if (loanId in ended || loanId in loans) return
        loans[loanId] = OpenLoan(loanId, toolId, neighborId, dueDate)
    }

    @Synchronized
    override fun extended(
        loanId: LoanId,
        dueDate: LocalDate,
    ) {
        loans[loanId]?.dueDate = dueDate
    }

    @Synchronized
    override fun end(loanId: LoanId) {
        loans.remove(loanId)
        ended.add(loanId)
    }

    @Synchronized
    override fun overdue(today: LocalDate): List<OverdueLoanToNotifyRow> =
        loans.values.filter { today.isAfter(it.dueDate) }.map {
            val borrower = neighbors[it.neighborId]
            OverdueLoanToNotifyRow(
                loanId = it.loanId,
                toolId = it.toolId,
                neighborId = it.neighborId,
                dueDate = it.dueDate,
                toolName = tools[it.toolId].orEmpty(),
                borrowerName = borrower?.name.orEmpty(),
                borrowerEmail = borrower?.email.orEmpty(),
            )
        }
}

@Configuration
class OverdueLoansToNotifyRepositoryConfiguration {
    @Bean
    fun overdueLoansToNotifyRepository(): OverdueLoansToNotifyRepository = InMemoryOverdueLoansToNotifyRepository()
}
