package com.toolshed.recordpickupnoticefailure

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.sendpickupnotice.PickupNoticeSent
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Pure decider tests: one or more per invariant boundary, no framework and no fixture. */
class RecordPickupNoticeFailureDeciderTest {
    private val now: Instant = Instant.parse("2026-10-06T16:00:00Z")
    private val collectBy = LocalDate.of(2026, 10, 9)
    private val hold = HoldId(UUID.randomUUID())
    private val drill = ToolId(UUID.randomUUID())
    private val ben = NeighborId(UUID.randomUUID())
    private val cara = NeighborId(UUID.randomUUID())

    /** A set-aside for Ben on the drill, with its pickup email sent. */
    private fun sentState(vararg more: Any): PickupNoticeState {
        val s = PickupNoticeState()
        s.evolve(PickupNoticeSent(hold, "ben@example.com", "Ben", "Drill", collectBy, now))
        s.evolve(HoldFulfilled(hold, drill, ben, collectBy, now))
        more.forEach { apply(s, it) }
        return s
    }

    private fun apply(
        s: PickupNoticeState,
        e: Any,
    ) {
        when (e) {
            is PickupNoticeFailed -> s.evolve(e)
            is HoldCancelled -> s.evolve(e)
            is HoldExpired -> s.evolve(e)
            is ToolCheckedOut -> s.evolve(e)
            is ToolRetired -> s.evolve(e)
            is ToolLost -> s.evolve(e)
            is NeighborAccountClosed -> s.evolve(e)
            else -> error("no handler for ${e::class.simpleName}")
        }
    }

    private fun decide(
        s: PickupNoticeState,
        reason: String,
    ) = RecordPickupNoticeFailureDecider.decide(RecordPickupNoticeFailure(hold, reason), s, now)

    private fun refusal(block: () -> Unit): RecordPickupNoticeFailureRefused =
        assertFailsWith<RecordPickupNoticeFailureRefused>(block = block)

    @Test
    fun `INV-PNF-3 - a refused report is recorded for the hold with the reason and the time`() {
        assertEquals(listOf(PickupNoticeFailed(hold, "refused", now)), decide(sentState(), "refused"))
    }

    @Test
    fun `INV-PNF-3 - a bounced report is recorded for the hold with the reason and the time`() {
        assertEquals(listOf(PickupNoticeFailed(hold, "bounced", now)), decide(sentState(), "bounced"))
    }

    @Test
    fun `INV-PNF-8 INV-PNF-5 - a report for a hold with no pickup email sent is refused no-matching-notice`() {
        val e = refusal { decide(PickupNoticeState(), "bounced") }
        assertEquals("no-matching-notice", e.reason)
        assertEquals("INV-PNF-8", e.invariant)
    }

    @Test
    fun `INV-PNF-9 - a holder who collected the tool ends the set-aside, so the report is refused hold-ended`() {
        val s = sentState(ToolCheckedOut(com.toolshed.axon.LoanId(UUID.randomUUID()), drill, ben, 7, collectBy, now))
        assertEquals("hold-ended", refusal { decide(s, "bounced") }.reason)
    }

    @Test
    fun `INV-PNF-9 - a checkout by someone else does not end the set-aside`() {
        val s = sentState(ToolCheckedOut(com.toolshed.axon.LoanId(UUID.randomUUID()), drill, cara, 7, collectBy, now))
        assertEquals(1, decide(s, "bounced").size)
    }

    @Test
    fun `INV-PNF-9 - a cancelled hold is refused hold-ended`() {
        assertEquals("hold-ended", refusal { decide(sentState(HoldCancelled(hold, now)), "refused") }.reason)
    }

    @Test
    fun `INV-PNF-9 - an expired hold is refused hold-ended`() {
        assertEquals("hold-ended", refusal { decide(sentState(HoldExpired(hold, now)), "refused") }.reason)
    }

    @Test
    fun `INV-PNF-9 - a retired tool ends the set-aside, and so does a tool recorded lost`() {
        assertEquals("hold-ended", refusal { decide(sentState(ToolRetired(drill, now)), "bounced") }.reason)
        val lost = com.toolshed.axon.LoanId(UUID.randomUUID())
        assertEquals("hold-ended", refusal { decide(sentState(ToolLost(lost, drill, now)), "bounced") }.reason)
    }

    @Test
    fun `INV-PNF-9 - the holder closing their account ends the set-aside`() {
        assertEquals("hold-ended", refusal { decide(sentState(NeighborAccountClosed(ben, false, now)), "bounced") }.reason)
    }

    @Test
    fun `INV-PNF-4 INV-PNF-10 - the same report again succeeds with nothing new recorded`() {
        val s = sentState(PickupNoticeFailed(hold, "bounced", now))
        assertTrue(decide(s, "bounced").isEmpty())
    }

    @Test
    fun `INV-PNF-4 INV-PNF-10 - a report with a different reason is refused failure-already-recorded`() {
        val s = sentState(PickupNoticeFailed(hold, "bounced", now))
        val e = refusal { decide(s, "refused") }
        assertEquals("failure-already-recorded", e.reason)
        assertEquals("INV-PNF-10", e.invariant)
    }

    @Test
    fun `INV-PNF-4 - a repeat after the set-aside ended is still the same failure, not hold-ended`() {
        val s = sentState(PickupNoticeFailed(hold, "bounced", now), HoldExpired(hold, now))
        assertTrue(decide(s, "bounced").isEmpty())
    }
}
