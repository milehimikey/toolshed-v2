package com.toolshed.axon

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * A provider's final failure report as it arrived, before anything in it is trusted. [rawBody] is the
 * report exactly as sent; [signature] is whatever the provider attached, if anything.
 */
data class FailureReport(
    val rawBody: String,
    val signature: String?,
)

/** What the verifier says about a report. Only [Verified] carries anything a slice may act on. */
sealed interface FailureReportVerdict {
    /** The report is genuine: [key] is the reference the shed gave the notice, [reason] the provider's reason. */
    data class Verified(
        val key: String,
        val reason: String,
    ) : FailureReportVerdict

    data class Rejected(
        val why: String,
    ) : FailureReportVerdict
}

/**
 * Verifies a signed provider failure report (Amendment 8). Provider-neutral: no scheme is chosen here.
 * A slice that takes failure reports (overdue notices, pickup notices) reads only [FailureReportVerdict.Verified].
 */
interface FailureReportVerifier {
    fun verify(report: FailureReport): FailureReportVerdict
}

/**
 * Rejects every report. This is the only verifier until a provider is chosen, so nothing is acted on
 * from a provider's report in the running app. It logs that a report was refused, never its contents.
 */
class FailClosedFailureReportVerifier : FailureReportVerifier {
    override fun verify(report: FailureReport): FailureReportVerdict {
        logger.info { "provider failure report rejected: no provider is selected" }
        return FailureReportVerdict.Rejected("no-provider-selected")
    }
}
