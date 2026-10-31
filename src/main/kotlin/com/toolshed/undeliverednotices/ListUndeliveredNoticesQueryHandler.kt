package com.toolshed.undeliverednotices

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/**
 * List undelivered notices: every entry with all its fields, resolved now from current facts. Listed by neighbor
 * name, ignoring case, then by when the failure was recorded (INV-UDN-9). No paging.
 */
@Component
class ListUndeliveredNoticesQueryHandler(
    private val repository: UndeliveredNoticesRepository,
) {
    @QueryHandler
    fun handle(query: ListUndeliveredNotices): UndeliveredNotices =
        UndeliveredNotices(
            repository
                .entries()
                .mapNotNull { entry -> toRow(entry)?.let { entry to it } }
                .sortedWith(
                    compareBy<Pair<StoredEntry, UndeliveredNoticeRow>>(
                        { it.second.neighborName.lowercase() },
                        { it.second.failedAt },
                    ).thenBy { it.first.sequence },
                ).map { it.second },
        )

    private fun toRow(entry: StoredEntry): UndeliveredNoticeRow? {
        val loan = entry.loanId?.let { repository.openLoanOf(it) }
        val setAside = entry.holdId?.let { repository.setAsideOf(it) }
        val neighborId = loan?.neighborId ?: setAside?.neighborId ?: return null
        val toolId = loan?.toolId ?: setAside?.toolId ?: return null
        return UndeliveredNoticeRow(
            kind = entry.kind,
            loanId = entry.loanId,
            holdId = entry.holdId,
            neighborId = neighborId,
            neighborName = repository.neighborName(neighborId).orEmpty(),
            neighborEmail = repository.neighborEmail(neighborId).orEmpty(),
            toolId = toolId,
            toolName = repository.toolName(toolId).orEmpty(),
            dueDate = loan?.dueDate,
            collectByDate = setAside?.collectByDate,
            reason = entry.reason,
            failedAt = entry.failedAt,
        )
    }
}
