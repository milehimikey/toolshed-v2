package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.LoanId
import com.toolshed.recordoverduenoticefailure.ProviderFailureReport.Interpretation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/** The translation's negative cases: an unsigned or wrongly signed report, or a bad shape, builds no command. */
class OverdueNoticeFailureTranslationTest {
    private val loan = UUID.nameUUIDFromBytes("l1".toByteArray())
    private val genuine =
        object : ProviderReportVerifier {
            override fun isGenuine(report: ProviderFailureReport) = report.signature == "genuine"
        }

    private fun report(
        reference: String? = loan.toString(),
        reason: String? = "refused",
        signature: String? = "genuine",
    ) = ProviderFailureReport(reference, reason, signature)

    @Test
    fun `INV-ONF-3 a genuine report with a loan and a refused reason builds the command`() {
        val result = ProviderFailureReport.interpret(report(), genuine)
        assertThat(result).isEqualTo(Interpretation.Dispatch(RecordOverdueNoticeFailure(LoanId(loan), "refused")))
    }

    @Test
    fun `INV-ONF-3 a genuine report with reason bounced builds the command`() {
        val result = ProviderFailureReport.interpret(report(reason = "bounced"), genuine)
        assertThat(result).isEqualTo(Interpretation.Dispatch(RecordOverdueNoticeFailure(LoanId(loan), "bounced")))
    }

    @Test
    fun `INV-ONF-3 an unsigned report builds no command`() {
        assertThat(ProviderFailureReport.interpret(report(signature = null), genuine))
            .isEqualTo(Interpretation.Dropped("signature-not-verified"))
    }

    @Test
    fun `INV-ONF-3 a wrongly signed report builds no command`() {
        assertThat(ProviderFailureReport.interpret(report(signature = "forged"), genuine))
            .isEqualTo(Interpretation.Dropped("signature-not-verified"))
    }

    @Test
    fun `INV-ONF-3 with the placeholder verifier every report is dropped`() {
        val failClosed = FailClosedProviderReportVerifier()
        assertThat(ProviderFailureReport.interpret(report(signature = "genuine"), failClosed))
            .isEqualTo(Interpretation.Dropped("signature-not-verified"))
    }

    @Test
    fun `INV-ONF-3 a reference that is not a loan UUID builds no command`() {
        assertThat(ProviderFailureReport.interpret(report(reference = "not-a-uuid"), genuine))
            .isEqualTo(Interpretation.Dropped("loan-reference-malformed"))
    }

    @Test
    fun `INV-ONF-3 a reason other than refused or bounced builds no command`() {
        assertThat(ProviderFailureReport.interpret(report(reason = "lost"), genuine))
            .isEqualTo(Interpretation.Dropped("reason-not-refused-or-bounced"))
    }

    @Test
    fun `INV-ONF-3 a missing reason builds no command`() {
        assertThat(ProviderFailureReport.interpret(report(reason = null), genuine))
            .isEqualTo(Interpretation.Dropped("reason-not-refused-or-bounced"))
    }
}
