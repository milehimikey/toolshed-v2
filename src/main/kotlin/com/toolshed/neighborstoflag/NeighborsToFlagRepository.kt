package com.toolshed.neighborstoflag

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * The in-memory read model behind Neighbors To Flag (Amendment 5). One row per late return that can still count
 * toward a flag, plus the bookkeeping the rows are decided from: each loan's borrower and each neighbor's status.
 * Every apply is idempotent (INV-NTF-8). Whether a row is still in the 90-day window is worked out when the list is
 * read (INV-NTF-2), never stored.
 */
@Repository
class NeighborsToFlagRepository {
    private data class Row(
        val neighborId: NeighborId,
        val loanId: LoanId,
        val returnedOn: LocalDate,
    )

    private val borrowers = HashMap<LoanId, NeighborId>()
    private val suspended = HashSet<NeighborId>()
    private val closed = HashSet<NeighborId>()
    private val returnsSeen = HashSet<LoanId>()
    private val rows = LinkedHashMap<LoanId, Row>()

    @Synchronized
    fun checkOut(
        loanId: LoanId,
        neighborId: NeighborId,
    ) {
        borrowers.putIfAbsent(loanId, neighborId)
    }

    /**
     * Tool Returned. Only a late return makes a row (INV-NTF-1), and not for a suspended or closed borrower
     * (INV-NTF-6, INV-NTF-7). A return already seen is ignored, so a replay can't put back a row a flag used up
     * (INV-NTF-3, INV-NTF-8).
     */
    @Synchronized
    fun returned(
        loanId: LoanId,
        returnedOn: LocalDate,
        late: Boolean,
    ) {
        if (!returnsSeen.add(loanId) || !late) return
        val borrower = borrowers[loanId] ?: return
        if (borrower in suspended || borrower in closed) return
        rows.putIfAbsent(loanId, Row(borrower, loanId, returnedOn))
    }

    /** Neighbor Flagged: the loans it names are used up and never count again (INV-NTF-3). */
    @Synchronized
    fun flagged(loanIds: List<LoanId>) {
        loanIds.forEach { rows.remove(it) }
    }

    /** Neighbor Suspended: the neighbor's rows go, and returns while suspended aren't counted (INV-NTF-7). */
    @Synchronized
    fun suspend(neighborId: NeighborId) {
        suspended.add(neighborId)
        removeRows(neighborId)
    }

    /** Neighbor Reinstated: the count starts again from zero (INV-NTF-5). */
    @Synchronized
    fun reinstate(neighborId: NeighborId) {
        suspended.remove(neighborId)
        removeRows(neighborId)
    }

    /** Neighbor Account Closed: the neighbor leaves the list and is never flagged (INV-NTF-6). */
    @Synchronized
    fun close(neighborId: NeighborId) {
        closed.add(neighborId)
        removeRows(neighborId)
    }

    private fun removeRows(neighborId: NeighborId) {
        rows.values.removeIf { it.neighborId == neighborId }
    }

    /**
     * Neighbors with at least [THRESHOLD] rows in the window: returnedOn is [today] or one of the 89 days before it
     * (INV-NTF-2). A return on 1 June counts through 29 August.
     */
    @Synchronized
    fun neighborsToFlag(today: LocalDate): List<NeighborToFlag> {
        val oldest = today.minusDays(WINDOW_DAYS - 1)
        return rows.values
            .filter { !it.returnedOn.isBefore(oldest) && !it.returnedOn.isAfter(today) }
            .groupBy { it.neighborId }
            .filterValues { it.size >= THRESHOLD }
            .map { (neighborId, inWindow) -> NeighborToFlag(neighborId, inWindow.map { it.loanId }) }
    }

    companion object {
        const val THRESHOLD = 3
        const val WINDOW_DAYS = 90L
    }
}
