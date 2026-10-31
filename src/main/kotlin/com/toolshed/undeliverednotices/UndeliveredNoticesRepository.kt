package com.toolshed.undeliverednotices

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/** Bookkeeping for an open loan: its tool, borrower and current due date. Never returned. */
data class OpenLoan(
    val toolId: ToolId,
    val neighborId: NeighborId,
    val dueDate: LocalDate,
)

/** Bookkeeping for a live set-aside: its tool, holder and collect-by date. Never returned. */
data class SetAside(
    val toolId: ToolId,
    val neighborId: NeighborId,
    val collectByDate: LocalDate,
)

/** An entry as stored: what the failure event carried plus a recording sequence. Names and dates resolve at read time. */
data class StoredEntry(
    val kind: NoticeKind,
    val loanId: LoanId?,
    val holdId: HoldId?,
    val reason: String,
    val failedAt: Instant,
    val sequence: Long,
)

/**
 * Undelivered Notices' own storage (Amendment 5): in-memory, rebuilt from the event stream on restart. Every
 * mutation leaves the store as it was after the first application of the same event (INV-UDN-8).
 */
interface UndeliveredNoticesRepository {
    fun saveToolName(
        toolId: ToolId,
        name: String,
    )

    fun saveNeighbor(
        neighborId: NeighborId,
        name: String,
        email: String,
    )

    fun changeEmail(
        neighborId: NeighborId,
        email: String,
    )

    fun openLoan(
        loanId: LoanId,
        loan: OpenLoan,
    )

    fun extendLoan(
        loanId: LoanId,
        dueDate: LocalDate,
    )

    fun endLoan(loanId: LoanId)

    fun setAside(
        holdId: HoldId,
        setAside: SetAside,
    )

    fun endSetAside(holdId: HoldId)

    fun endSetAsidesForToolAndNeighbor(
        toolId: ToolId,
        neighborId: NeighborId,
    )

    fun endSetAsidesOnTool(toolId: ToolId)

    fun endSetAsidesForNeighbor(neighborId: NeighborId)

    fun overdueFailed(
        loanId: LoanId,
        reason: String,
        failedAt: Instant,
    )

    fun pickupFailed(
        holdId: HoldId,
        reason: String,
        failedAt: Instant,
    )

    fun entries(): List<StoredEntry>

    fun toolName(toolId: ToolId): String?

    fun neighborName(neighborId: NeighborId): String?

    fun neighborEmail(neighborId: NeighborId): String?

    fun openLoanOf(loanId: LoanId): OpenLoan?

    fun setAsideOf(holdId: HoldId): SetAside?
}

class InMemoryUndeliveredNoticesRepository : UndeliveredNoticesRepository {
    private val lock = Any()
    private val toolNames = HashMap<ToolId, String>()
    private val names = HashMap<NeighborId, String>()
    private val emails = HashMap<NeighborId, String>()
    private val openLoans = HashMap<LoanId, OpenLoan>()
    private val endedLoans = HashSet<LoanId>()
    private val setAsides = HashMap<HoldId, SetAside>()
    private val endedHolds = HashSet<HoldId>()
    private val overdueEntries = HashMap<LoanId, StoredEntry>()
    private val pickupEntries = HashMap<HoldId, StoredEntry>()
    private val sequence = AtomicLong(0)

    override fun saveToolName(
        toolId: ToolId,
        name: String,
    ) = synchronized(lock) {
        toolNames[toolId] = name
        Unit
    }

    override fun saveNeighbor(
        neighborId: NeighborId,
        name: String,
        email: String,
    ) = synchronized(lock) {
        names[neighborId] = name
        // A repeated Neighbor Registered must not undo a later Neighbor Email Changed already applied.
        emails.putIfAbsent(neighborId, email)
        Unit
    }

    override fun changeEmail(
        neighborId: NeighborId,
        email: String,
    ) = synchronized(lock) {
        emails[neighborId] = email
        Unit
    }

    override fun openLoan(
        loanId: LoanId,
        loan: OpenLoan,
    ) = synchronized(lock) {
        // INV-UDN-8: a repeated Tool Checked Out must neither reset an extended due date nor reopen an ended loan.
        if (loanId !in endedLoans) openLoans.putIfAbsent(loanId, loan)
        Unit
    }

    override fun extendLoan(
        loanId: LoanId,
        dueDate: LocalDate,
    ) = synchronized(lock) {
        openLoans.computeIfPresent(loanId) { _, loan -> loan.copy(dueDate = dueDate) }
        Unit
    }

    override fun endLoan(loanId: LoanId) =
        synchronized(lock) {
            endedLoans.add(loanId)
            openLoans.remove(loanId)
            overdueEntries.remove(loanId)
            Unit
        }

    override fun setAside(
        holdId: HoldId,
        setAside: SetAside,
    ) = synchronized(lock) {
        if (holdId !in endedHolds) setAsides.putIfAbsent(holdId, setAside)
        Unit
    }

    override fun endSetAside(holdId: HoldId) = synchronized(lock) { endHold(holdId) }

    override fun endSetAsidesForToolAndNeighbor(
        toolId: ToolId,
        neighborId: NeighborId,
    ) = synchronized(lock) { endWhere { it.toolId == toolId && it.neighborId == neighborId } }

    override fun endSetAsidesOnTool(toolId: ToolId) = synchronized(lock) { endWhere { it.toolId == toolId } }

    override fun endSetAsidesForNeighbor(neighborId: NeighborId) = synchronized(lock) { endWhere { it.neighborId == neighborId } }

    private fun endWhere(matches: (SetAside) -> Boolean) {
        setAsides
            .filterValues(matches)
            .keys
            .toList()
            .forEach { endHold(it) }
    }

    private fun endHold(holdId: HoldId) {
        endedHolds.add(holdId)
        setAsides.remove(holdId)
        pickupEntries.remove(holdId)
    }

    override fun overdueFailed(
        loanId: LoanId,
        reason: String,
        failedAt: Instant,
    ) = synchronized(lock) {
        // Only an open loan gets an entry (nothing brings one back), and a repeat creates no second one (INV-UDN-8).
        if (loanId in openLoans && loanId !in overdueEntries) {
            overdueEntries[loanId] = StoredEntry(NoticeKind.OVERDUE_NOTICE, loanId, null, reason, failedAt, sequence.incrementAndGet())
        }
        Unit
    }

    override fun pickupFailed(
        holdId: HoldId,
        reason: String,
        failedAt: Instant,
    ) = synchronized(lock) {
        if (holdId in setAsides && holdId !in pickupEntries) {
            pickupEntries[holdId] = StoredEntry(NoticeKind.PICKUP_NOTICE, null, holdId, reason, failedAt, sequence.incrementAndGet())
        }
        Unit
    }

    override fun entries(): List<StoredEntry> = synchronized(lock) { overdueEntries.values + pickupEntries.values }

    override fun toolName(toolId: ToolId): String? = synchronized(lock) { toolNames[toolId] }

    override fun neighborName(neighborId: NeighborId): String? = synchronized(lock) { names[neighborId] }

    override fun neighborEmail(neighborId: NeighborId): String? = synchronized(lock) { emails[neighborId] }

    override fun openLoanOf(loanId: LoanId): OpenLoan? = synchronized(lock) { openLoans[loanId] }

    override fun setAsideOf(holdId: HoldId): SetAside? = synchronized(lock) { setAsides[holdId] }
}

@Configuration
class UndeliveredNoticesRepositoryConfiguration {
    @Bean
    fun undeliveredNoticesRepository(): UndeliveredNoticesRepository = InMemoryUndeliveredNoticesRepository()
}
