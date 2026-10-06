package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.LoanId
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The email provider's final failure report for an overdue notice. The reference is the loan ID the
 * shed handed over. [signature] is whatever the provider attached; how it is produced and checked is
 * not yet decided (see the open gap on this slice), so the verifier below is a port.
 */
data class ProviderFailureReport(
    val reference: String?,
    val reason: String?,
    val signature: String?,
) {
    sealed interface Interpretation {
        data class Dispatch(
            val command: RecordOverdueNoticeFailure,
        ) : Interpretation

        data class Dropped(
            val why: String,
        ) : Interpretation
    }

    companion object {
        /** Checks the signature, then the shape. An unsigned or wrongly signed report builds no command. */
        fun interpret(
            report: ProviderFailureReport,
            verifier: ProviderReportVerifier,
        ): Interpretation {
            if (!verifier.isGenuine(report)) return Interpretation.Dropped("signature-not-verified")
            val loanId =
                report.reference?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return Interpretation.Dropped("loan-reference-malformed")
            val reason = report.reason
            if (reason == null || reason !in RecordOverdueNoticeFailure.FAILURE_REASONS) {
                return Interpretation.Dropped("reason-not-refused-or-bounced")
            }
            return Interpretation.Dispatch(RecordOverdueNoticeFailure(LoanId(loanId), reason))
        }
    }
}

/**
 * Decides whether a provider report carries a genuine signature. The real scheme is not decided, so
 * the only bean is one that fails closed: every report is dropped until a scheme is chosen.
 */
interface ProviderReportVerifier {
    fun isGenuine(report: ProviderFailureReport): Boolean
}

@Component
class FailClosedProviderReportVerifier : ProviderReportVerifier {
    override fun isGenuine(report: ProviderFailureReport): Boolean = false
}
