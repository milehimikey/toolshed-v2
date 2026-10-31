package com.toolshed.undeliverednotices

import com.toolshed.web.Caller
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Undelivered Notices over REST (Amendment 3): the shed manager's screen alone (INV-UDN-10). */
@RestController
class UndeliveredNoticesController(
    private val queries: QueryGateway,
) {
    @GetMapping("/api/undelivered-notices")
    fun list(caller: Caller): List<UndeliveredNoticeResponse> {
        caller.requireUndeliveredNoticesReader()
        return queries
            .query(ListUndeliveredNotices, UndeliveredNotices::class.java)
            .join()
            .entries
            .map { it.toResponse() }
    }
}

data class UndeliveredNoticeResponse(
    val kind: String,
    val loanId: UUID?,
    val holdId: UUID?,
    val neighborId: UUID,
    val neighborName: String,
    val neighborEmail: String,
    val toolId: UUID,
    val toolName: String,
    val dueDate: LocalDate?,
    val collectByDate: LocalDate?,
    val reason: String,
    val failedAt: Instant,
)

private fun UndeliveredNoticeRow.toResponse() =
    UndeliveredNoticeResponse(
        kind = kind.wireName,
        loanId = loanId?.value,
        holdId = holdId?.value,
        neighborId = neighborId.value,
        neighborName = neighborName,
        neighborEmail = neighborEmail,
        toolId = toolId.value,
        toolName = toolName,
        dueDate = dueDate,
        collectByDate = collectByDate,
        reason = reason,
        failedAt = failedAt,
    )
