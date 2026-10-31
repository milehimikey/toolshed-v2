package com.toolshed.flaggedneighbors

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * Flagged Neighbors' own storage (Amendment 5): in-memory, rebuilt from the event stream on restart. Every
 * mutation leaves the store as it was after the first application of the same event (INV-FLN-9).
 */
interface FlaggedNeighborsRepository {
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

    fun checkedOut(
        loanId: LoanId,
        loan: LoanFacts,
    )

    fun extendLoan(
        loanId: LoanId,
        dueDate: LocalDate,
    )

    fun returned(
        loanId: LoanId,
        returnedOn: LocalDate,
    )

    fun flag(
        neighborId: NeighborId,
        loanIds: List<LoanId>,
        flaggedAt: Instant,
    )

    fun remove(neighborId: NeighborId)

    fun entries(): List<StoredEntry>

    fun toolName(toolId: ToolId): String?

    fun neighborName(neighborId: NeighborId): String?

    fun neighborEmail(neighborId: NeighborId): String?

    fun loanOf(loanId: LoanId): LoanFacts?
}

class InMemoryFlaggedNeighborsRepository : FlaggedNeighborsRepository {
    private val lock = Any()
    private val toolNames = HashMap<ToolId, String>()
    private val names = HashMap<NeighborId, String>()
    private val emails = HashMap<NeighborId, String>()
    private val loans = HashMap<LoanId, LoanFacts>()
    private val entries = HashMap<NeighborId, StoredEntry>()
    private val appliedFlags = HashSet<Triple<NeighborId, List<LoanId>, Instant>>()
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

    override fun checkedOut(
        loanId: LoanId,
        loan: LoanFacts,
    ) = synchronized(lock) {
        // INV-FLN-9: a repeated Tool Checked Out must neither reset an extended due date nor clear a return.
        loans.putIfAbsent(loanId, loan)
        Unit
    }

    override fun extendLoan(
        loanId: LoanId,
        dueDate: LocalDate,
    ) = synchronized(lock) {
        loans.computeIfPresent(loanId) { _, loan -> loan.copy(dueDate = dueDate) }
        Unit
    }

    override fun returned(
        loanId: LoanId,
        returnedOn: LocalDate,
    ) = synchronized(lock) {
        loans.computeIfPresent(loanId) { _, loan -> loan.copy(returnedOn = loan.returnedOn ?: returnedOn) }
        Unit
    }

    override fun flag(
        neighborId: NeighborId,
        loanIds: List<LoanId>,
        flaggedAt: Instant,
    ) = synchronized(lock) {
        // INV-FLN-9: the same Neighbor Flagged applied again is recognised and changes nothing, even after the entry is gone.
        if (!appliedFlags.add(Triple(neighborId, loanIds, flaggedAt))) return@synchronized Unit
        val flag = StoredFlag(loanIds, flaggedAt)
        val existing = entries[neighborId]
        entries[neighborId] =
            if (existing == null) {
                StoredEntry(neighborId, listOf(flag), sequence.incrementAndGet())
            } else {
                existing.copy(flags = existing.flags + flag)
            }
        Unit
    }

    override fun remove(neighborId: NeighborId) =
        synchronized(lock) {
            entries.remove(neighborId)
            Unit
        }

    override fun entries(): List<StoredEntry> = synchronized(lock) { entries.values.toList() }

    override fun toolName(toolId: ToolId): String? = synchronized(lock) { toolNames[toolId] }

    override fun neighborName(neighborId: NeighborId): String? = synchronized(lock) { names[neighborId] }

    override fun neighborEmail(neighborId: NeighborId): String? = synchronized(lock) { emails[neighborId] }

    override fun loanOf(loanId: LoanId): LoanFacts? = synchronized(lock) { loans[loanId] }
}

@Configuration
class FlaggedNeighborsRepositoryConfiguration {
    @Bean
    fun flaggedNeighborsRepository(): FlaggedNeighborsRepository = InMemoryFlaggedNeighborsRepository()
}
