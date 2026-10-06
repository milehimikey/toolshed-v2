package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.EmailKey
import com.toolshed.axon.FailureReport
import com.toolshed.axon.FailureReportVerdict
import com.toolshed.axon.FailureReportVerifier
import com.toolshed.web.CommandDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * The email provider's failure report, turned into Record Overdue Notice Failure. The foundation's verifier
 * decides whether a report is genuine and decodes its key once (Amendment 8); this translation takes only a
 * verified report for an overdue notice, and drops everything else before any command exists.
 */
@Component
class OverdueNoticeFailureTranslation(
    private val verifier: FailureReportVerifier,
    private val dispatcher: CommandDispatcher,
) {
    fun receive(report: FailureReport) {
        val command = commandFor(verifier.verify(report))
        if (command == null) return
        try {
            // A refusal is re-thrown by the dispatcher as CommandRejected; the doc keeps it for diagnosing the provider link.
            dispatcher.send(command, Any::class.java)
        } catch (refused: CommandRejected) {
            logger.info { "overdue-notice-failure refused invariant=${refused.invariant} reason=${refused.reason}" }
        }
    }

    companion object {
        /** The command a verified overdue-notice report builds, or null when the report is dropped. */
        fun commandFor(verdict: FailureReportVerdict): RecordOverdueNoticeFailure? =
            when (verdict) {
                is FailureReportVerdict.Rejected -> {
                    logger.info { "overdue-notice-failure report dropped why=${verdict.why}" }
                    null
                }
                is FailureReportVerdict.Verified -> {
                    val key = verdict.key as? EmailKey.ForLoan
                    if (key == null) {
                        logger.info { "overdue-notice-failure report dropped: not an overdue notice key" }
                        null
                    } else if (verdict.reason !in RecordOverdueNoticeFailure.FAILURE_REASONS) {
                        logger.info { "overdue-notice-failure report dropped: reason is not refused or bounced" }
                        null
                    } else {
                        RecordOverdueNoticeFailure(key.loanId, verdict.reason)
                    }
                }
            }
    }
}
