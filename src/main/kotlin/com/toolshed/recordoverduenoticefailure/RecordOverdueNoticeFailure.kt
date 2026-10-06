package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.LoanId
import org.axonframework.modelling.annotation.TargetEntityId

/**
 * Records that the provider's final report said an overdue notice for [loanId] failed. [reason] is
 * `refused` or `bounced`; the translation rejects any other value before a command is ever built.
 */
data class RecordOverdueNoticeFailure(
    val loanId: LoanId,
    val reason: String,
) {
    init {
        require(reason in FAILURE_REASONS) { "reason must be one of $FAILURE_REASONS" }
    }

    @TargetEntityId
    private fun ledger(): NoticeLedger = NoticeLedger

    companion object {
        val FAILURE_REASONS = setOf("refused", "bounced")
    }
}

/** The consistency boundary for notice failures: one id for the shed's notices and loans. Not a loan. */
object NoticeLedger
