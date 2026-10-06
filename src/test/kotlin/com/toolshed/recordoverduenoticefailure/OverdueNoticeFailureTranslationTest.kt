package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.EmailKey
import com.toolshed.axon.FailureReportVerdict
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/** The translation's decision: a verified overdue-notice report builds the command; anything else builds nothing. */
class OverdueNoticeFailureTranslationTest {
    private val loan = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))

    @Test
    fun `INV-ONF-3 a verified report for an overdue notice with reason refused builds the command`() {
        val command = OverdueNoticeFailureTranslation.commandFor(FailureReportVerdict.Verified(EmailKey.ForLoan(loan), "refused"))
        assertThat(command).isEqualTo(RecordOverdueNoticeFailure(loan, "refused"))
    }

    @Test
    fun `INV-ONF-3 a verified report with reason bounced builds the command`() {
        val command = OverdueNoticeFailureTranslation.commandFor(FailureReportVerdict.Verified(EmailKey.ForLoan(loan), "bounced"))
        assertThat(command).isEqualTo(RecordOverdueNoticeFailure(loan, "bounced"))
    }

    @Test
    fun `INV-ONF-3 a rejected report builds no command`() {
        assertThat(OverdueNoticeFailureTranslation.commandFor(FailureReportVerdict.Rejected("no-provider-selected"))).isNull()
    }

    @Test
    fun `INV-ONF-3 a verified report keyed to a hold, not a loan, builds no command`() {
        val hold = EmailKey.ForHold(HoldId(UUID.nameUUIDFromBytes("h1".toByteArray())))
        assertThat(OverdueNoticeFailureTranslation.commandFor(FailureReportVerdict.Verified(hold, "refused"))).isNull()
    }

    @Test
    fun `INV-ONF-3 a verified report with a reason other than refused or bounced builds no command`() {
        assertThat(OverdueNoticeFailureTranslation.commandFor(FailureReportVerdict.Verified(EmailKey.ForLoan(loan), "lost"))).isNull()
    }
}
