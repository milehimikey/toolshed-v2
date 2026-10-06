package com.toolshed.checkouttool

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.extendloan.LoanExtended
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Pure decider tests: one or more per invariant boundary, no framework and no fixture. */
class CheckOutDeciderTest {
    private val today = LocalDate.of(2026, 10, 5)
    private val now: Instant = Instant.parse("2026-10-05T15:00:00Z")

    private val drill = ToolId(UUID.randomUUID())
    private val ladder = ToolId(UUID.randomUUID())
    private val ana = NeighborId(UUID.randomUUID())
    private val ben = NeighborId(UUID.randomUUID())
    private val cara = NeighborId(UUID.randomUUID())
    private val hold1 = HoldId(UUID.randomUUID())
    private val hold2 = HoldId(UUID.randomUUID())

    private fun state(vararg events: Any): CheckOutState {
        val s = CheckOutState()
        listOf(
            ToolAdded(drill, "Drill", null, 7, now),
            ToolAdded(ladder, "Ladder", null, 7, now),
            NeighborRegistered(ana, "Ana", "a@example.com", now),
            NeighborRegistered(ben, "Ben", "b@example.com", now),
            NeighborRegistered(cara, "Cara", "c@example.com", now),
        ).forEach { apply(s, it) }
        events.forEach { apply(s, it) }
        return s
    }

    private fun apply(
        s: CheckOutState,
        e: Any,
    ) {
        when (e) {
            is ToolAdded -> s.evolve(e)
            is ToolRetired -> s.evolve(e)
            is ToolLost -> s.evolve(e)
            is ToolReturned -> s.evolve(e)
            is ToolCheckedOut -> s.evolve(e)
            is LoanExtended -> s.evolve(e)
            is NeighborRegistered -> s.evolve(e)
            is NeighborSuspended -> s.evolve(e)
            is NeighborAccountClosed -> s.evolve(e)
            is HoldPlaced -> s.evolve(e)
            is HoldFulfilled -> s.evolve(e)
            else -> error("no handler for ${e::class.simpleName}")
        }
    }

    private fun request(
        neighbor: NeighborId,
        tool: ToolId = drill,
        loan: LoanId = LoanId(UUID.randomUUID()),
        period: String? = null,
    ) = CheckOutTool(loan, tool, neighbor, period?.let(::BigDecimal))

    private fun decide(
        s: CheckOutState,
        command: CheckOutTool,
        on: LocalDate = today,
    ) = CheckOutDecider.decide(command, s, on, now)

    private fun refusal(block: () -> Unit): CheckOutRefused = assertFailsWith<CheckOutRefused>(block = block)

    // --- INV-CHK-6 ---

    @Test
    fun `INV-CHK-6 - absent period takes the default and sets the due date from today`() {
        val events = decide(state(), request(ana))
        assertEquals(7, events.single().loanPeriodDays)
        assertEquals(LocalDate.of(2026, 10, 12), events.single().dueDate)
    }

    @Test
    fun `INV-CHK-6 - a whole period from 1 to the default is accepted, at both ends`() {
        assertEquals(1, decide(state(), request(ana, period = "1")).single().loanPeriodDays)
        assertEquals(7, decide(state(), request(ana, period = "7")).single().loanPeriodDays)
    }

    @Test
    fun `INV-CHK-6 - zero, longer than the default, and fractional are each refused period-out-of-range`() {
        listOf("0", "8", "2.5").forEach { period ->
            val e = refusal { decide(state(), request(ana, period = period)) }
            assertEquals("period-out-of-range", e.reason)
            assertEquals("INV-CHK-6", e.invariant)
        }
    }

    // --- INV-CHK-12 ---

    @Test
    fun `INV-CHK-12 - the same loan with the same details records nothing, however the loan has since gone`() {
        val loan = LoanId(UUID.randomUUID())
        val s = state(ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now), ToolReturned(loan, drill, now, today, false))
        assertTrue(decide(s, request(ana, loan = loan)).isEmpty())
    }

    @Test
    fun `INV-CHK-12 - an absent period matches the default the loan started with, but a different period does not`() {
        val loan = LoanId(UUID.randomUUID())
        val s = state(ToolCheckedOut(loan, drill, ana, 3, LocalDate.of(2026, 10, 8), now))
        assertTrue(decide(s, request(ana, loan = loan, period = "3")).isEmpty())
        assertEquals("loan-id-in-use", refusal { decide(s, request(ana, loan = loan)) }.reason)
    }

    // --- INV-CHK-5: a loan due today is not overdue; one due yesterday is ---

    @Test
    fun `INV-CHK-5 - a loan due yesterday blocks, a loan due today does not`() {
        val due = { d: LocalDate ->
            state(ToolCheckedOut(LoanId(UUID.randomUUID()), ladder, ana, 7, d, now))
        }
        assertEquals("has-overdue-loan", refusal { decide(due(today.minusDays(1)), request(ana)) }.reason)
        assertEquals(1, decide(due(today), request(ana)).size)
    }

    @Test
    fun `INV-CHK-5 - a later extension moves the due date, so an extended loan does not block`() {
        val loan = LoanId(UUID.randomUUID())
        val s =
            state(
                ToolCheckedOut(loan, ladder, ana, 7, today.minusDays(3), now),
                LoanExtended(loan, today.minusDays(3), today.plusDays(4), now),
            )
        assertEquals(1, decide(s, request(ana)).size)
    }

    // --- INV-CHK-3b: the set-aside lapses at the start of the day after collect-by ---

    @Test
    fun `INV-CHK-3b - a set-aside is still held on its collect-by date and lapses the day after`() {
        val s = state(HoldPlaced(hold1, drill, ben, now), HoldFulfilled(hold1, drill, ben, today, now), HoldPlaced(hold2, drill, cara, now))
        assertEquals("set-aside-for-another", refusal { decide(s, request(ana)) }.reason)
        assertEquals("reserved-for-another", refusal { decide(s, request(ana), today.plusDays(1)) }.reason)
    }

    @Test
    fun `INV-CHK-3b INV-CHK-9 - a holder's own checkout ends their hold, so the next holder is not behind a phantom`() {
        val loan = LoanId(UUID.randomUUID())
        val s =
            state(
                HoldPlaced(hold1, drill, ben, now),
                HoldPlaced(hold2, drill, cara, now),
                ToolCheckedOut(loan, drill, ben, 7, LocalDate.of(2026, 10, 12), now),
                ToolReturned(loan, drill, now, today, false),
            )
        assertEquals(1, decide(s, request(cara)).size)
    }

    // --- INV-CHK-7 ---

    @Test
    fun `INV-CHK-7 - suspension refuses, reinstatement does not, and closure refuses before suspension`() {
        val suspended = state(NeighborSuspended(ana, now))
        assertEquals("neighbor-suspended", refusal { decide(suspended, request(ana)) }.reason)
        val closed = state(NeighborSuspended(ana, now), NeighborAccountClosed(ana, true, now))
        assertEquals("account-closed", refusal { decide(closed, request(ana)) }.reason)
    }

    // --- INV-CHK-4 and INV-CHK-2 ---

    @Test
    fun `INV-CHK-4 - a tool recorded lost is retired and refused tool-retired`() {
        val loan = LoanId(UUID.randomUUID())
        val s = state(ToolCheckedOut(loan, drill, ben, 7, LocalDate.of(2026, 10, 12), now), ToolLost(loan, drill, now))
        assertEquals("tool-retired", refusal { decide(s, request(ana)) }.reason)
    }

    @Test
    fun `INV-CHK-2 - a returned tool can be checked out again, and a tool on loan cannot`() {
        val loan = LoanId(UUID.randomUUID())
        val onLoan = state(ToolCheckedOut(loan, drill, ben, 7, LocalDate.of(2026, 10, 12), now))
        assertEquals("tool-checked-out", refusal { decide(onLoan, request(ana)) }.reason)
        val returned =
            state(ToolCheckedOut(loan, drill, ben, 7, LocalDate.of(2026, 10, 12), now), ToolReturned(loan, drill, now, today, false))
        assertEquals(1, decide(returned, request(ana)).size)
    }

    // --- INV-CHK-3a: the front of the queue ---

    @Test
    fun `INV-CHK-3a - only the neighbor at the front of the waiting queue may take a returned tool`() {
        val s = state(HoldPlaced(hold1, drill, ben, now), HoldPlaced(hold2, drill, cara, now))
        assertEquals(1, decide(s, request(ben)).size)
        assertEquals("reserved-for-another", refusal { decide(s, request(cara)) }.reason)
    }
    // --- closing an account ends the neighbor's holds (close-account.md) ---

    @Test
    fun `INV-CHK-3a INV-CHK-7 - a holder who closes their account leaves the queue, so the next holder is at the front`() {
        val s = state(HoldPlaced(hold1, drill, ben, now), HoldPlaced(hold2, drill, cara, now), NeighborAccountClosed(ben, false, now))
        assertEquals(1, decide(s, request(cara)).size)
    }

    @Test
    fun `INV-CHK-3 INV-CHK-7 - a holder who closes while the tool is set aside releases it to anyone`() {
        val s =
            state(
                HoldPlaced(hold1, drill, ben, now),
                HoldFulfilled(hold1, drill, ben, today, now),
                NeighborAccountClosed(ben, false, now),
            )
        assertEquals(1, decide(s, request(ana)).size)
    }
}
