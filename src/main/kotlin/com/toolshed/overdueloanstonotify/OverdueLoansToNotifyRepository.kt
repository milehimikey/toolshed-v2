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

    /** Sets the loan's due date (INV-OLN-2); a loan that has left the view is unaffected. */
    fun extended(
        loanId: LoanId,
        dueDate: LocalDate,
    )

    /** The loan's notice went out, or it was returned or lost; it never comes back (INV-OLN-3, INV-OLN-4, INV-OLN-8). */
    fun end(loanId: LoanId)

    /** Every row whose due date is before [today], with the current names and email (INV-OLN-1, INV-OLN-6). */
    fun overdue(today: LocalDate): List<LoanToNotifyRow>
}

class InMemoryOverdueLoansToNotifyRepository : OverdueLoansToNotifyRepository {
    private class Open(
        val toolId: ToolId,
        val neighborId: NeighborId,
        var dueDate: LocalDate,
    )

    private class Neighbor(
        var name: String,
        var email: String,
    )

    private val toolNames = HashMap<ToolId, String>()
    private val neighbors = HashMap<NeighborId, Neighbor>()
    private val rows = LinkedHashMap<LoanId, Open>()

    /** Nothing puts a row back, so a replayed Tool Checked Out for a loan that has left must not revive it. */
    private val ended = HashSet<LoanId>()

    @Synchronized
    override fun toolAdded(
        toolId: ToolId,
        name: String,
    ) {
        toolNames.putIfAbsent(toolId, name)
    }

    @Synchronized
    override fun neighborRegistered(
        neighborId: NeighborId,
        name: String,
        email: String,
    ) {
        // A replayed registration must not undo a later email change.
        neighbors.putIfAbsent(neighborId, Neighbor(name, email))
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
        if (loanId in ended || loanId in rows) return
        rows[loanId] = Open(toolId, neighborId, dueDate)
    }

    @Synchronized
    override fun extended(
        loanId: LoanId,
        dueDate: LocalDate,
    ) {
        rows[loanId]?.dueDate = dueDate
    }

    @Synchronized
    override fun end(loanId: LoanId) {
        rows.remove(loanId)
        ended.add(loanId)
    }

    @Synchronized
    override fun overdue(today: LocalDate): List<LoanToNotifyRow> =
        rows.entries.mapNotNull { (loanId, open) ->
            if (!today.isAfter(open.dueDate)) return@mapNotNull null
            val toolName = toolNames[open.toolId] ?: return@mapNotNull null
            val neighbor = neighbors[open.neighborId] ?: return@mapNotNull null
            LoanToNotifyRow(loanId, open.toolId, open.neighborId, open.dueDate, toolName, neighbor.name, neighbor.email)
        }
}

@Configuration
class OverdueLoansToNotifyRepositoryConfiguration {
    @Bean
    fun overdueLoansToNotifyRepository(): OverdueLoansToNotifyRepository = InMemoryOverdueLoansToNotifyRepository()
}
