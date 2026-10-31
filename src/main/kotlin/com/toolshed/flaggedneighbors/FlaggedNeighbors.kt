package com.toolshed.flaggedneighbors

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import java.time.Instant
import java.time.LocalDate

/** One late return behind a flag (the view's LateReturn field table). */
data class LateReturnRow(
    val loanId: LoanId,
    val toolName: String,
    val dueDate: LocalDate,
    val returnedOn: LocalDate,
    val flaggedAt: Instant,
)

/** One row of Flagged Neighbors (the view's field table), derived at read time. */
data class FlaggedNeighborRow(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val flagCount: Int,
    val latestFlaggedAt: Instant,
    val lateReturns: List<LateReturnRow>,
)

/** The query's answer: every entry, ordered for the screen (INV-FLN-10). No paging. */
data class FlaggedNeighbors(
    val entries: List<FlaggedNeighborRow>,
)

/** The query the Flagged Neighbors screen asks. Takes nothing. */
object ListFlaggedNeighbors

/** Identity checks that run before the query is asked (Amendment 6, INV-FLN-11): signed in, and the shed manager's role. */
fun Caller.requireFlaggedNeighborsReader() {
    requireShedManager(
        notSignedIn = Refusal("INV-FLN-11", "Please sign in."),
        notShedManager = Refusal("INV-FLN-11", "Only the shed manager can see this screen."),
    )
}

/** Bookkeeping for a loan: its tool, borrower, current due date and (once returned) return date. Never returned. */
data class LoanFacts(
    val toolId: ToolId,
    val neighborId: NeighborId,
    val dueDate: LocalDate,
    val returnedOn: LocalDate? = null,
)

/** One Neighbor Flagged as kept on an entry. */
data class StoredFlag(
    val loanIds: List<LoanId>,
    val flaggedAt: Instant,
)

/** An entry as stored: its flags in the order they landed, and a creation sequence. Names and dates resolve at read time. */
data class StoredEntry(
    val neighborId: NeighborId,
    val flags: List<StoredFlag>,
    val sequence: Long,
)
