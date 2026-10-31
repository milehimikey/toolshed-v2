package com.toolshed.expirehold

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.retiretool.ToolRetired
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** The pure decision, with no Axon fixture: each boundary, and the order the first failure is reported in. */
class ExpireHoldDeciderTest {
    private val denver = ZoneId.of("America/Denver")
    private val placed = Instant.parse("2026-10-14T16:00:00Z")

    // 00:00 and 23:59 shed time (MDT, UTC-6) around the 17 October collect-by date.
    private val startOfOct18 = Instant.parse("2026-10-18T06:00:00Z")
    private val endOfOct17 = Instant.parse("2026-10-18T05:59:00Z")

    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"))
    private val cara = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"))
    private val h1 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"))
    private val h2 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e2"))
    private val h9 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e9"))

    private val placedH1 = HoldPlaced(h1, drill, ben, placed)
    private val placedH2 = HoldPlaced(h2, drill, cara, placed)
    private val fulfilledH1 = HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 17), placed)

    private fun stateWith(vararg events: Any): ExpireState {
        val state = ExpireState()
        events.forEach { event ->
            when (event) {
                is HoldPlaced -> state.evolve(event)
                is HoldFulfilled -> state.evolve(event)
                is HoldCancelled -> state.evolve(event)
                is HoldExpired -> state.evolve(event)
                is ToolCheckedOut -> state.evolve(event)
                is ToolRetired -> state.evolve(event)
                is NeighborAccountClosed -> state.evolve(event)
                else -> error("unexpected event ${event::class.simpleName}")
            }
        }
        return state
    }

    private fun decide(
        state: ExpireState,
        hold: HoldId = h1,
        at: Instant = startOfOct18,
    ) = ExpireHoldDecider.decide(ExpireHold(hold), state, at, denver)

    private fun reasonOf(
        state: ExpireState,
        hold: HoldId = h1,
        at: Instant = startOfOct18,
    ): String = assertThrows<ExpireHoldRejected> { decide(state, hold, at) }.reason

    private fun checkedOutBy(neighbor: NeighborId) =
        ToolCheckedOut(LoanId(UUID.randomUUID()), drill, neighbor, 7, LocalDate.of(2026, 10, 25), placed)

    // --- INV-EXP-2 ---
    @Test
    fun `INV-EXP-2 - given H1 set aside collect by 17 October, when handled at start of 18 October, then Hold Expired`() {
        assertThat(decide(stateWith(placedH1, fulfilledH1))).containsExactly(HoldExpired(h1, startOfOct18))
    }

    @Test
    fun `INV-EXP-2 - given H1 set aside collect by 17 October, when handled at 23-59 on 17 October, then refused pickup-window-open`() {
        val refused = assertThrows<ExpireHoldRejected> { decide(stateWith(placedH1, fulfilledH1), at = endOfOct17) }
        assertThat(refused.invariant).isEqualTo("INV-EXP-2")
        assertThat(refused.reason).isEqualTo("pickup-window-open")
        assertThat(refused.message).isEqualTo("This hold's pickup window hasn't ended.")
    }

    // --- INV-EXP-3 ---
    @Test
    fun `INV-EXP-3 - given the holder checked out, when handled, then refused hold-ended`() {
        assertThat(reasonOf(stateWith(placedH1, fulfilledH1, checkedOutBy(ben)))).isEqualTo("hold-ended")
    }

    @Test
    fun `INV-EXP-3 - given the hold was cancelled, when handled, then refused hold-ended`() {
        assertThat(reasonOf(stateWith(placedH1, fulfilledH1, HoldCancelled(h1, placed)))).isEqualTo("hold-ended")
    }

    @Test
    fun `INV-EXP-3 - given the tool was retired, when handled, then refused hold-ended`() {
        assertThat(reasonOf(stateWith(placedH1, fulfilledH1, ToolRetired(drill, placed)))).isEqualTo("hold-ended")
    }

    @Test
    fun `INV-EXP-3 - given the holder closed their account, when handled, then refused hold-ended`() {
        assertThat(reasonOf(stateWith(placedH1, fulfilledH1, NeighborAccountClosed(ben, false, placed)))).isEqualTo("hold-ended")
    }

    @Test
    fun `INV-EXP-3 - given someone else checked the tool out after the cut-off, when handled, then Hold Expired`() {
        val state = stateWith(placedH1, placedH2, fulfilledH1, checkedOutBy(cara))
        assertThat(decide(state)).containsExactly(HoldExpired(h1, startOfOct18))
    }

    // --- INV-EXP-4 ---
    @Test
    fun `INV-EXP-4 - given the holder cannot collect, when handled, then the decision looks only at the window`() {
        // Nothing about suspension or overdue loans is in the state: the same events decide the same way for any holder.
        assertThat(decide(stateWith(placedH1, fulfilledH1))).hasSize(1)
    }

    // --- INV-EXP-10 ---
    @Test
    fun `INV-EXP-10 - given H2 never fulfilled, when handled, then refused hold-not-set-aside`() {
        assertThat(reasonOf(stateWith(placedH1, placedH2, fulfilledH1), h2)).isEqualTo("hold-not-set-aside")
    }

    @Test
    fun `INV-EXP-10 - given no hold H9, when handled, then refused hold-not-found`() {
        assertThat(reasonOf(stateWith(placedH1, fulfilledH1), h9)).isEqualTo("hold-not-found")
    }

    // --- INV-EXP-11 ---
    @Test
    fun `INV-EXP-11 - given Hold Expired already recorded, when handled again, then nothing is recorded`() {
        assertThat(decide(stateWith(placedH1, fulfilledH1, HoldExpired(h1, startOfOct18)))).isEmpty()
    }

    // --- order of checks ---
    @Test
    fun `INV-EXP-11 - given the hold expired and the window check would fail earlier, when handled, then the repeat still succeeds`() {
        assertThat(decide(stateWith(placedH1, fulfilledH1, HoldExpired(h1, startOfOct18)), at = endOfOct17)).isEmpty()
    }

    @Test
    fun `INV-EXP-3 INV-EXP-10 - given a waiting hold that ended, when handled, then hold-ended comes before hold-not-set-aside`() {
        assertThat(reasonOf(stateWith(placedH2, HoldCancelled(h2, placed)), h2)).isEqualTo("hold-ended")
    }

    @Test
    fun `INV-EXP-3 INV-EXP-2 - given a hold that ended inside the window, when handled, then hold-ended comes before pickup-window-open`() {
        assertThat(reasonOf(stateWith(placedH1, fulfilledH1, HoldCancelled(h1, placed)), at = endOfOct17)).isEqualTo("hold-ended")
    }
}
