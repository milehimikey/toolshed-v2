package com.toolshed.recordpickupnoticefailure

import com.toolshed.axon.EmailKey
import com.toolshed.axon.FailureReportVerdict
import com.toolshed.axon.HoldId
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The translation's pure mapping: a verified report about a hold with a legal reason becomes the command; anything else builds nothing. */
class PickupFailureReportTranslationTest {
    private val hold = HoldId(UUID.randomUUID())

    @Test
    fun `a verified refused report about a hold becomes the command`() {
        val command = PickupFailureReportTranslation.commandFor(FailureReportVerdict.Verified(EmailKey.ForHold(hold), "refused"))
        assertEquals(RecordPickupNoticeFailure(hold, "refused"), command)
    }

    @Test
    fun `a verified bounced report about a hold becomes the command`() {
        val command = PickupFailureReportTranslation.commandFor(FailureReportVerdict.Verified(EmailKey.ForHold(hold), "bounced"))
        assertEquals(RecordPickupNoticeFailure(hold, "bounced"), command)
    }

    @Test
    fun `negative - an unsigned or wrongly signed report builds no command`() {
        assertNull(PickupFailureReportTranslation.commandFor(FailureReportVerdict.Rejected("signature-not-verified")))
    }

    @Test
    fun `negative - a verified report keyed to a loan, not a hold, builds no command`() {
        val loan = com.toolshed.axon.LoanId(UUID.randomUUID())
        assertNull(PickupFailureReportTranslation.commandFor(FailureReportVerdict.Verified(EmailKey.ForLoan(loan), "refused")))
    }

    @Test
    fun `negative - a verified report with any other reason builds no command`() {
        assertNull(PickupFailureReportTranslation.commandFor(FailureReportVerdict.Verified(EmailKey.ForHold(hold), "lost")))
    }
}
