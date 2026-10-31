package com.toolshed.undeliverednotices

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import java.time.Instant
import java.time.LocalDate

/** Which failed notice an entry stands for (field table: `kind`). */
enum class NoticeKind(
    val wireName: String,
) {
    OVERDUE_NOTICE("overdue-notice"),
    PICKUP_NOTICE("pickup-notice"),
}

/** One row of Undelivered Notices (the view's field table), derived at read time. */
data class UndeliveredNoticeRow(
    val kind: NoticeKind,
    val loanId: LoanId?,
    val holdId: HoldId?,
    val neighborId: NeighborId,
    val neighborName: String,
    val neighborEmail: String,
    val toolId: ToolId,
    val toolName: String,
    val dueDate: LocalDate?,
    val collectByDate: LocalDate?,
    val reason: String,
    val failedAt: Instant,
)

/** The query's answer: every entry, ordered for the screen (INV-UDN-9). No paging. */
data class UndeliveredNotices(
    val entries: List<UndeliveredNoticeRow>,
)

/** The query the Undelivered Notices screen asks. Takes nothing. */
object ListUndeliveredNotices

/** Identity checks that run before the query is asked (Amendment 6, INV-UDN-10): signed in, and the shed manager's role. */
fun Caller.requireUndeliveredNoticesReader() {
    requireShedManager(
        notSignedIn = Refusal("INV-UDN-10", "Please sign in."),
        notShedManager = Refusal("INV-UDN-10", "Only the shed manager can see this screen."),
    )
}
