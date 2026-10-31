package com.toolshed.flagneighbor

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Pure decisions for Flag Neighbor, one boundary at a time. Today is 5 October 2026. */
class FlagNeighborDeciderTest {
    private val now = Instant.parse("2026-10-05T15:00:00Z")
    private val today = LocalDate.of(2026, 10, 5)
    private val n1 = NeighborId(UUID.nameUUIDFromBytes("n1".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val l2 = LoanId(UUID.nameUUIDFromBytes("l2".toByteArray()))
    private val l3 = LoanId(UUID.nameUUIDFromBytes("l3".toByteArray()))
    private val l4 = LoanId(UUID.nameUUIDFromBytes("l4".toByteArray()))

    private fun late(
        loan: LoanId,
        on: LocalDate,
    ) = LateReturn(loan, on)

    private fun facts(
        registered: Boolean = true,
        suspended: Boolean = false,
        closed: Boolean = false,
        lateReturns: List<LateReturn> = emptyList(),
        flaggedSets: List<Set<LoanId>> = emptyList(),
    ) = FlagFacts(registered, suspended, closed, lateReturns, flaggedSets)

    private val three = listOf(late(l1, today.minusDays(34)), late(l2, today.minusDays(15)), late(l3, today))

    private fun reason(
        command: FlagNeighbor,
        facts: FlagFacts,
    ): String = assertFailsWith<FlagNeighborRejected> { FlagNeighborDecider.decide(command, facts, now, today) }.reason

    @Test
    fun `INV-FLG-2 INV-FLG-3 INV-FLG-4 - given three counting late returns, when flagged, then only Neighbor Flagged is recorded`() {
        val events = FlagNeighborDecider.decide(FlagNeighbor(n1, listOf(l1, l2, l3)), facts(lateReturns = three), now, today)
        assertEquals(listOf(NeighborFlagged(n1, listOf(l1, l2, l3), now)), events)
    }

    @Test
    fun `INV-FLG-4 - given four counting late returns, when flagged with all four, then all four are named`() {
        val four = three + late(l4, today)
        val events = FlagNeighborDecider.decide(FlagNeighbor(n1, listOf(l1, l2, l3, l4)), facts(lateReturns = four), now, today)
        assertEquals(listOf(l1, l2, l3, l4), events.single().loanIds)
    }

    @Test
    fun `INV-FLG-4 - given four counting late returns, when flagged with only three, then late-returns-changed`() {
        assertEquals("late-returns-changed", reason(FlagNeighbor(n1, listOf(l1, l2, l3)), facts(lateReturns = three + late(l4, today))))
    }

    @Test
    fun `INV-FLG-4 - given the order of the loans differs, when flagged, then recorded because order does not matter`() {
        val events = FlagNeighborDecider.decide(FlagNeighbor(n1, listOf(l3, l1, l2)), facts(lateReturns = three), now, today)
        assertEquals(1, events.size)
    }

    @Test
    fun `INV-FLG-2 - given two counting late returns, when flagged, then below-threshold`() {
        assertEquals("below-threshold", reason(FlagNeighbor(n1, listOf(l1, l2)), facts(lateReturns = three.take(2))))
    }

    @Test
    fun `INV-FLG-2 - given a late return 90 days back, when flagged with all three, then below-threshold`() {
        val old = listOf(late(l1, LocalDate.of(2026, 6, 1)), late(l2, LocalDate.of(2026, 9, 20)), late(l3, today))
        assertEquals("below-threshold", reason(FlagNeighbor(n1, listOf(l1, l2, l3)), facts(lateReturns = old)))
    }

    @Test
    fun `INV-FLG-2 - given a late return on the 89th day before today, when flagged with all three, then recorded`() {
        val edge = listOf(late(l1, LocalDate.of(2026, 7, 8)), late(l2, LocalDate.of(2026, 9, 20)), late(l3, today))
        assertEquals(1, FlagNeighborDecider.decide(FlagNeighbor(n1, listOf(l1, l2, l3)), facts(lateReturns = edge), now, today).size)
    }

    @Test
    fun `INV-FLG-2 - given a late return on the 90th day before today, when flagged, then it does not count`() {
        val edge = listOf(late(l1, LocalDate.of(2026, 7, 7)), late(l2, LocalDate.of(2026, 9, 20)), late(l3, today))
        assertEquals("below-threshold", reason(FlagNeighbor(n1, listOf(l1, l2, l3)), facts(lateReturns = edge)))
    }

    @Test
    fun `INV-FLG-4 INV-FLG-2 - given the count is only on loans not yet used up, when used-up loans are named too, then below-threshold`() {
        // L1-L3 were used up (the state drops them); L5 alone counts.
        assertEquals(
            "below-threshold",
            reason(
                FlagNeighbor(n1, listOf(l1, l2, l3, l4)),
                facts(lateReturns = listOf(late(l4, today)), flaggedSets = listOf(setOf(l1, l2, l3))),
            ),
        )
    }

    @Test
    fun `INV-FLG-4 - given three new counting returns, when an old loan is named with them, then late-returns-changed`() {
        val l5 = LoanId(UUID.nameUUIDFromBytes("l5".toByteArray()))
        val fresh = listOf(late(l4, today), late(l5, today), late(LoanId(UUID.nameUUIDFromBytes("l6".toByteArray())), today))
        assertEquals(
            "late-returns-changed",
            reason(
                FlagNeighbor(n1, listOf(l1, l4, l5, fresh[2].loanId)),
                facts(lateReturns = fresh, flaggedSets = listOf(setOf(l1, l2, l3))),
            ),
        )
    }

    @Test
    fun `INV-FLG-7 - given an earlier flag still waiting, when three new late returns are flagged, then recorded again`() {
        val fresh =
            listOf(
                late(l4, today),
                late(LoanId(UUID.nameUUIDFromBytes("l5".toByteArray())), today),
                late(LoanId(UUID.nameUUIDFromBytes("l6".toByteArray())), today),
            )
        val command = FlagNeighbor(n1, fresh.map { it.loanId })
        assertEquals(
            1,
            FlagNeighborDecider.decide(command, facts(lateReturns = fresh, flaggedSets = listOf(setOf(l1, l2, l3))), now, today).size,
        )
    }

    @Test
    fun `INV-FLG-9 - given a flag already names exactly these loans, when flagged again, then succeeds and records nothing`() {
        val events =
            FlagNeighborDecider.decide(
                FlagNeighbor(n1, listOf(l1, l2, l3)),
                facts(flaggedSets = listOf(setOf(l1, l2, l3))),
                now,
                today,
            )
        assertEquals(emptyList(), events)
    }

    @Test
    fun `INV-FLG-9 - given a flag names exactly these loans, when the neighbor is since closed, then the repeat still succeeds`() {
        val events =
            FlagNeighborDecider.decide(
                FlagNeighbor(n1, listOf(l1, l2, l3)),
                facts(closed = true, flaggedSets = listOf(setOf(l1, l2, l3))),
                now,
                today,
            )
        assertEquals(emptyList(), events)
    }

    @Test
    fun `INV-FLG-5 - given a suspended neighbor, when flagged, then neighbor-suspended`() {
        assertEquals("neighbor-suspended", reason(FlagNeighbor(n1, listOf(l1, l2, l3)), facts(suspended = true, lateReturns = three)))
    }

    @Test
    fun `INV-FLG-5 - given a closed account, when flagged, then account-closed`() {
        assertEquals("account-closed", reason(FlagNeighbor(n1, listOf(l1, l2, l3)), facts(closed = true, lateReturns = three)))
    }

    @Test
    fun `INV-FLG-5 - given an account closed while suspended, when flagged, then account-closed comes first`() {
        assertEquals(
            "account-closed",
            reason(FlagNeighbor(n1, listOf(l1, l2, l3)), facts(closed = true, suspended = true, lateReturns = three)),
        )
    }

    @Test
    fun `INV-FLG-5 - given the count was reset by reinstatement, when the old loans are named, then below-threshold`() {
        assertEquals("below-threshold", reason(FlagNeighbor(n1, listOf(l1, l2, l3)), facts(lateReturns = emptyList())))
    }

    @Test
    fun `INV-FLG-10 - given no such neighbor, when flagged, then neighbor-not-found`() {
        assertEquals("neighbor-not-found", reason(FlagNeighbor(n1, listOf(l1, l2, l3)), facts(registered = false)))
    }

    @Test
    fun `INV-FLG-2 - given an empty loan list, when the command is built, then it is bad input`() {
        assertFailsWith<IllegalArgumentException> { FlagNeighbor(n1, emptyList()) }
    }
}
