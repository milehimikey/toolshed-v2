package com.toolshed.sendoverduenotice

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.returntool.ToolReturned
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Pure decider tests, no framework and no fixture: each boundary, and the order the first failure is reported in. */
class SendOverdueNoticeDeciderTest {
    private val denver = ZoneId.of("America/Denver")
    private val oct12 = LocalDate.of(2026, 10, 12)
    private val oct19 = LocalDate.of(2026, 10, 19)
    private val checkedOutAt: Instant = Instant.parse("2026-10-05T16:00:00Z")
    private val oct13Start: Instant = LocalDate.of(2026, 10, 13).atStartOfDay(denver).toInstant()
    private val now: Instant = Instant.parse("2026-10-13T16:00:00Z")
    private val l1 = LoanId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"))

    private fun command(
        sentTo: String = "ana@example.org",
        dueDate: LocalDate = oct12,
    ) = SendOverdueNotice(l1, sentTo, "Ana Ruiz", "Cordless drill", dueDate)

    private fun out() =
        SendOverdueNoticeState().also {
            it.evolve(ToolCheckedOut(l1, drill, ana, 7, oct12, checkedOutAt))
        }

    private val sent = OverdueNoticeSent(l1, "ana@example.org", "Ana Ruiz", "Cordless drill", oct12, now)

    private fun reasonOf(
        state: SendOverdueNoticeState,
        command: SendOverdueNotice = command(),
        at: Instant = now,
    ): String = assertFailsWith<SendOverdueNoticeRejected> { SendOverdueNoticeDecider.decide(command, state, at, denver) }.reason

    // --- INV-OVN-6 / INV-OVN-3 ---
    @Test
    fun `INV-OVN-6 INV-OVN-3 - given L1 is overdue, when decided, then Overdue Notice Sent carries the given content`() {
        assertEquals(listOf(sent), SendOverdueNoticeDecider.decide(command(), out(), now, denver))
    }

    @Test
    fun `INV-OVN-3 - given the list's current address, when decided, then that address is recorded, trimmed`() {
        val events = SendOverdueNoticeDecider.decide(command(sentTo = "  ana.ruiz@example.net "), out(), now, denver)
        assertEquals("ana.ruiz@example.net", events.single().sentTo)
    }

    @Test
    fun `INV-OVN-6 - given the due date itself, until its last moment, when decided, then refused loan-not-overdue`() {
        assertEquals("loan-not-overdue", reasonOf(out(), at = oct13Start.minusSeconds(1)))
    }

    @Test
    fun `INV-OVN-6 - given the start of the day after the due date, when decided, then it is recorded`() {
        assertEquals(1, SendOverdueNoticeDecider.decide(command(), out(), oct13Start, denver).size)
    }

    @Test
    fun `INV-OVN-6 - given the due date was extended, when decided with the new date before it passes, then refused loan-not-overdue`() {
        val state = out().also { it.evolve(LoanExtended(l1, oct12, oct19, checkedOutAt)) }
        assertEquals("loan-not-overdue", reasonOf(state, command(dueDate = oct19)))
    }

    // --- INV-OVN-2 ---
    @Test
    fun `INV-OVN-2 - given the notice was sent, when the same content arrives again, then nothing is recorded`() {
        assertTrue(SendOverdueNoticeDecider.decide(command(), out().also { it.evolve(sent) }, now, denver).isEmpty())
    }

    @Test
    fun `INV-OVN-2 - given the notice was sent, when different content arrives, then refused notice-already-sent`() {
        assertEquals("notice-already-sent", reasonOf(out().also { it.evolve(sent) }, command(sentTo = "ana.ruiz@example.net")))
    }

    @Test
    fun `INV-OVN-2 INV-OVN-9 - given the notice was sent and the loan has since ended, when the same content arrives, then a repeat`() {
        val state = out().also { it.evolve(sent) }
        state.evolve(ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 13), true))
        assertTrue(SendOverdueNoticeDecider.decide(command(), state, now, denver).isEmpty())
    }

    // --- INV-OVN-5 ---
    @Test
    fun `INV-OVN-5 - given the drill was returned, when decided, then refused loan-ended`() {
        val state = out().also { it.evolve(ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 13), true)) }
        assertEquals("loan-ended", reasonOf(state))
    }

    @Test
    fun `INV-OVN-5 - given Tool Lost was recorded, when decided, then refused loan-ended`() {
        assertEquals("loan-ended", reasonOf(out().also { it.evolve(ToolLost(l1, drill, now)) }))
    }

    // --- INV-OVN-10 ---
    @Test
    fun `INV-OVN-10 - given no loan was checked out, when decided, then refused loan-not-found`() {
        assertEquals("loan-not-found", reasonOf(SendOverdueNoticeState()))
    }

    // --- INV-OVN-11 ---
    @Test
    fun `INV-OVN-11 - given the loan was extended to 19 October, when 12 October arrives, then refused due-date-changed`() {
        val state = out().also { it.evolve(LoanExtended(l1, oct12, oct19, checkedOutAt)) }
        assertEquals("due-date-changed", reasonOf(state))
    }

    // --- order of the checks ---
    @Test
    fun `INV-OVN-10 INV-OVN-2 INV-OVN-5 INV-OVN-11 INV-OVN-6 - several failures, doc order picks the reason`() {
        val early = checkedOutAt
        val wrong = command(dueDate = oct19)
        val returned = ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 13), true)
        assertEquals("loan-not-found", reasonOf(SendOverdueNoticeState(), wrong, early))
        assertEquals("notice-already-sent", reasonOf(out().also { it.evolve(sent) }, command(sentTo = "x@example.org"), early))
        assertEquals("loan-ended", reasonOf(out().also { it.evolve(returned) }, wrong, early))
        assertEquals("due-date-changed", reasonOf(out(), wrong, early))
        assertEquals("loan-not-overdue", reasonOf(out(), command(), early))
    }
}
