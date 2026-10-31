package com.toolshed.sendpickupnotice

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.retiretool.ToolRetired
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Pure decider tests, no framework and no fixture: each boundary, and the order the first failure is reported in. */
class SendPickupNoticeDeciderTest {
    private val denver = ZoneId.of("America/Denver")
    private val now: Instant = Instant.parse("2026-10-14T16:00:00Z")
    private val oct17 = LocalDate.of(2026, 10, 17)
    private val h1 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"))
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val saw = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d2"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"))
    private val cara = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"))

    private fun command(
        sentTo: String = "ben@example.org",
        collectBy: LocalDate = oct17,
        hold: HoldId = h1,
    ) = SendPickupNotice(hold, sentTo, "Ben Ito", "Cordless drill", collectBy)

    private fun placed() = SendPickupNoticeState().also { it.evolve(HoldPlaced(h1, drill, ben, now)) }

    private fun setAside(vararg more: Any): SendPickupNoticeState {
        val s = placed()
        s.evolve(HoldFulfilled(h1, drill, ben, oct17, now))
        more.forEach { e ->
            when (e) {
                is HoldCancelled -> s.evolve(e)
                is HoldExpired -> s.evolve(e)
                is ToolCheckedOut -> s.evolve(e)
                is ToolRetired -> s.evolve(e)
                is NeighborAccountClosed -> s.evolve(e)
                is PickupNoticeSent -> s.evolve(e)
                else -> error("unexpected ${e::class.simpleName}")
            }
        }
        return s
    }

    private fun reasonOf(
        state: SendPickupNoticeState,
        command: SendPickupNotice = command(),
        at: Instant = now,
    ): String = assertFailsWith<SendPickupNoticeRejected> { SendPickupNoticeDecider.decide(command, state, at, denver) }.reason

    private val sent = PickupNoticeSent(h1, "ben@example.org", "Ben Ito", "Cordless drill", oct17, now)

    // --- INV-PKN-6 / INV-PKN-3 ---
    @Test
    fun `INV-PKN-6 INV-PKN-3 - given H1 is set aside, when the command is decided, then Pickup Notice Sent carries the given content`() {
        val events = SendPickupNoticeDecider.decide(command(), setAside(), now, denver)
        assertEquals(listOf(sent), events)
    }

    @Test
    fun `INV-PKN-3 - given the list's current address, when decided, then that address is recorded, trimmed`() {
        val events = SendPickupNoticeDecider.decide(command(sentTo = "  ben.ito@example.net "), setAside(), now, denver)
        assertEquals("ben.ito@example.net", events.single().sentTo)
    }

    // --- INV-PKN-2 ---
    @Test
    fun `INV-PKN-2 - given the notice was sent, when the same content arrives again, then nothing is recorded`() {
        assertTrue(SendPickupNoticeDecider.decide(command(), setAside(sent), now, denver).isEmpty())
    }

    @Test
    fun `INV-PKN-2 - given the notice was sent, when different content arrives, then refused notice-already-sent`() {
        assertEquals("notice-already-sent", reasonOf(setAside(sent), command(sentTo = "ben.ito@example.net")))
    }

    @Test
    fun `INV-PKN-2 - given the notice was sent and the hold has since ended, when the same content arrives, then still a repeat`() {
        assertTrue(SendPickupNoticeDecider.decide(command(), setAside(sent, HoldCancelled(h1, now)), now, denver).isEmpty())
    }

    // --- INV-PKN-5 ---
    @Test
    fun `INV-PKN-5 - given Ben checked the drill out, when decided, then refused hold-ended`() {
        assertEquals("hold-ended", reasonOf(setAside(ToolCheckedOut(UUID.randomUUID().let(::loan), drill, ben, 7, oct17, now))))
    }

    @Test
    fun `INV-PKN-5 - given a checkout of the drill by someone else or a different tool, when decided, then the hold has not ended`() {
        val state = setAside(ToolCheckedOut(loan(UUID.randomUUID()), drill, cara, 7, oct17, now))
        state.evolve(ToolCheckedOut(loan(UUID.randomUUID()), saw, ben, 7, oct17, now))
        assertEquals(1, SendPickupNoticeDecider.decide(command(), state, now, denver).size)
    }

    @Test
    fun `INV-PKN-5 - given H1 was cancelled, when decided, then refused hold-ended`() {
        assertEquals("hold-ended", reasonOf(setAside(HoldCancelled(h1, now))))
    }

    @Test
    fun `INV-PKN-5 - given H1 expired, when decided, then refused hold-ended`() {
        assertEquals("hold-ended", reasonOf(setAside(HoldExpired(h1, now))))
    }

    @Test
    fun `INV-PKN-5 - given the drill was retired, when decided, then refused hold-ended`() {
        assertEquals("hold-ended", reasonOf(setAside(ToolRetired(drill, now))))
    }

    @Test
    fun `INV-PKN-5 - given another tool was retired, when decided, then the hold has not ended`() {
        assertEquals(1, SendPickupNoticeDecider.decide(command(), setAside(ToolRetired(saw, now)), now, denver).size)
    }

    @Test
    fun `INV-PKN-5 - given Ben closed his account, when decided, then refused hold-ended`() {
        assertEquals("hold-ended", reasonOf(setAside(NeighborAccountClosed(ben, false, now))))
    }

    @Test
    fun `INV-PKN-5 - given another neighbor closed their account, when decided, then the hold has not ended`() {
        assertEquals(1, SendPickupNoticeDecider.decide(command(), setAside(NeighborAccountClosed(cara, false, now)), now, denver).size)
    }

    // --- INV-PKN-9 ---
    @Test
    fun `INV-PKN-9 - given no hold was placed, when decided, then refused hold-not-found`() {
        assertEquals("hold-not-found", reasonOf(SendPickupNoticeState()))
    }

    // --- INV-PKN-10 ---
    @Test
    fun `INV-PKN-10 - given H1 is waiting, when decided, then refused hold-not-set-aside`() {
        assertEquals("hold-not-set-aside", reasonOf(placed()))
    }

    // --- INV-PKN-11 ---
    @Test
    fun `INV-PKN-11 - given a different collect-by date, when decided, then refused collect-by-date-mismatch`() {
        assertEquals("collect-by-date-mismatch", reasonOf(setAside(), command(collectBy = LocalDate.of(2026, 10, 18))))
    }

    // --- INV-PKN-12 ---
    @Test
    fun `INV-PKN-12 - given today is the collect-by date until its last moment, when decided, then it is recorded`() {
        val lastMoment =
            LocalDate
                .of(2026, 10, 18)
                .atStartOfDay(denver)
                .toInstant()
                .minusSeconds(1)
        assertEquals(1, SendPickupNoticeDecider.decide(command(), setAside(), lastMoment, denver).size)
    }

    @Test
    fun `INV-PKN-12 - given the start of the day after the collect-by date, when decided, then refused pickup-window-ended`() {
        val start = LocalDate.of(2026, 10, 18).atStartOfDay(denver).toInstant()
        assertEquals("pickup-window-ended", reasonOf(setAside(), at = start))
    }

    // --- order of the checks ---
    @Test
    fun `INV-PKN-9 INV-PKN-2 INV-PKN-5 INV-PKN-10 INV-PKN-11 INV-PKN-12 - several failures, doc order picks the reason`() {
        val late = LocalDate.of(2026, 10, 18).atStartOfDay(denver).toInstant()
        val wrong = command(collectBy = LocalDate.of(2026, 10, 19))
        assertEquals("hold-not-found", reasonOf(SendPickupNoticeState(), wrong, late))
        assertEquals("notice-already-sent", reasonOf(setAside(sent, HoldCancelled(h1, now)), command(sentTo = "x@example.org"), late))
        assertEquals("hold-ended", reasonOf(setAside(HoldCancelled(h1, now)), wrong, late))
        assertEquals("hold-not-set-aside", reasonOf(placed(), wrong, late))
        assertEquals("collect-by-date-mismatch", reasonOf(setAside(), wrong, late))
        assertEquals("pickup-window-ended", reasonOf(setAside(), command(), late))
    }

    private fun loan(id: UUID) = com.toolshed.axon.LoanId(id)
}
