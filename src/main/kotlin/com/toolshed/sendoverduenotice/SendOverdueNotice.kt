package com.toolshed.sendoverduenotice

import com.toolshed.axon.LoanId
import org.axonframework.modelling.annotation.TargetEntityId
import java.time.LocalDate

/**
 * Record that the overdue email for a loan is being sent. Internal: only the Overdue Notice Gateway issues it, with the
 * content from the Overdue Loans To Notify item. The content is compared and recorded after trimming (Amendment 7).
 */
data class SendOverdueNotice(
    val loanId: LoanId,
    val sentTo: String,
    val borrowerName: String,
    val toolName: String,
    val dueDate: LocalDate,
) {
    init {
        require(sentTo.isNotBlank()) { "sentTo is required" }
        require(borrowerName.isNotBlank()) { "borrowerName is required" }
        require(toolName.isNotBlank()) { "toolName is required" }
    }

    @TargetEntityId
    private fun key() = OverdueNoticeKey(loanId)
}

/** The entity id for one loan's overdue email. A data class, because a value-class id is stored as a bare UUID. */
data class OverdueNoticeKey(
    val loanId: LoanId,
)
