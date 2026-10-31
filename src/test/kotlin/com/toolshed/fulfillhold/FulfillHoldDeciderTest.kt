package com.toolshed.fulfillhold

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** The pure decision, with no Axon fixture: each boundary, and the order the first failure is reported in. */
class FulfillHoldDeciderTest {
    private val denver = ZoneId.of("America/Denver")

    // 10 October, 10:00 in Denver.
    private val now = Instant.parse("2026-10-10T16:00:00Z")
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val saw = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d2"))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"))
    private val cara = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"))
    private val loan = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))
    private val h1 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"))
    private val h2 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e2"))
    private val h3 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e3"))
    private val h9 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e9"))

    private val placedH1 = HoldPlaced(h1, drill, ben, now)
    private val placedH2 = HoldPlaced(h2, drill, cara, now)
    private val checkedOut = ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now)
    private val returned = ToolReturned(loan, drill, now, LocalDate.of(2026, 10, 10), false)
    private val fulfilledH1 = HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 13), now)

    private fun stateWith(vararg events: Any): FulfillState {
        val state = FulfillState()
        events.forEach { event ->
            when (event) {
                is HoldPlaced -> state.evolve(event)
                is HoldFulfilled -> state.evolve(event)
                is HoldCancelled -> state.evolve(event)
                is HoldExpired -> state.evolve(event)
                is ToolCheckedOut -> state.evolve(event)
                is ToolReturned -> state.evolve(event)
                is ToolLost -> state.evolve(event)
                is ToolRetired -> state.evolve(event)
                is NeighborAccountClosed -> state.evolve(event)
                else -> error("unexpected event ${event::class.simpleName}")
            }
        }
        return state
    }

    private fun fulfill(
        state: FulfillState,
        hold: HoldId,
        at: Instant = now,
    ) = FulfillHoldDecider.decide(FulfillHold(hold), state, at, denver)

    private fun refusedWith(
        reason: String,
        block: () -> Unit,
    ) {
        assertThat(assertThrows<FulfillHoldRejected>(block).reason).isEqualTo(reason)
    }

    // --- INV-FUL-2 ---

    @Test
    fun `INV-FUL-2 - given H1 and H2 waiting on a returned tool, when H1 is fulfilled, then the front hold is set aside`() {
        val events = fulfill(stateWith(placedH1, placedH2, checkedOut, returned), h1)
        assertThat(events).containsExactly(HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 13), now))
    }

    @Test
    fun `INV-FUL-2 - given H1 and H2 waiting, when H2 is fulfilled, then refused not-front-of-queue`() {
        refusedWith("not-front-of-queue") { fulfill(stateWith(placedH1, placedH2), h2) }
    }

    @Test
    fun `INV-FUL-2 - given H1 cancelled and H2 waiting, when H2 is fulfilled, then H2 is now the front`() {
        val events = fulfill(stateWith(placedH1, placedH2, HoldCancelled(h1, now)), h2)
        assertThat(events.single().holdId).isEqualTo(h2)
    }

    @Test
    fun `INV-FUL-2 - given a hold on another tool, when H2 is fulfilled, then that hold does not stand in front`() {
        val sawHold = HoldPlaced(h3, saw, ana, now)
        val events = fulfill(stateWith(sawHold, placedH1, placedH2, HoldCancelled(h1, now)), h2)
        assertThat(events.single().holdId).isEqualTo(h2)
    }

    // --- INV-FUL-3 ---

    @Test
    fun `INV-FUL-3 - given the holder checked out first, when H1 is fulfilled, then refused hold-ended`() {
        val holderChecksOut = ToolCheckedOut(LoanId(UUID.randomUUID()), drill, ben, 7, LocalDate.of(2026, 10, 12), now)
        refusedWith("hold-ended") { fulfill(stateWith(placedH1, placedH2, checkedOut, returned, holderChecksOut), h1) }
    }

    @Test
    fun `INV-FUL-3 - given H1 cancelled, when H1 is fulfilled, then refused hold-ended`() {
        refusedWith("hold-ended") { fulfill(stateWith(placedH1, placedH2, HoldCancelled(h1, now)), h1) }
    }

    @Test
    fun `INV-FUL-3 - given the tool is retired, when H1 is fulfilled, then refused hold-ended`() {
        refusedWith("hold-ended") { fulfill(stateWith(placedH1, ToolRetired(drill, now)), h1) }
    }

    @Test
    fun `INV-FUL-3 - given the tool is recorded lost, when H1 is fulfilled, then refused hold-ended`() {
        refusedWith("hold-ended") { fulfill(stateWith(placedH1, checkedOut, ToolLost(loan, drill, now)), h1) }
    }

    @Test
    fun `INV-FUL-3 - given the holder closed their account, when H1 is fulfilled, then refused hold-ended`() {
        refusedWith("hold-ended") { fulfill(stateWith(placedH1, NeighborAccountClosed(ben, false, now)), h1) }
    }

    @Test
    fun `INV-FUL-3 - given the tool is on loan, when H1 is fulfilled, then refused tool-checked-out`() {
        refusedWith("tool-checked-out") { fulfill(stateWith(placedH1, checkedOut), h1) }
    }

    @Test
    fun `INV-FUL-3 - given the loan was returned, when H1 is fulfilled, then the open loan no longer blocks it`() {
        assertThat(fulfill(stateWith(placedH1, checkedOut, returned), h1)).hasSize(1)
    }

    @Test
    fun `INV-FUL-3 - given a hold ended and the tool is on loan, when fulfilled, then hold-ended is reported before tool-checked-out`() {
        refusedWith("hold-ended") { fulfill(stateWith(placedH1, checkedOut, HoldCancelled(h1, now)), h1) }
    }

    // --- INV-FUL-4 ---

    @Test
    fun `INV-FUL-4 - given the tool is set aside for Ben, when H2 is fulfilled, then refused tool-set-aside`() {
        refusedWith("tool-set-aside") { fulfill(stateWith(placedH1, placedH2, fulfilledH1), h2) }
    }

    @Test
    fun `INV-FUL-4 - given the set-aside lapsed with no Hold Expired, when H2 is fulfilled, then still tool-set-aside`() {
        val muchLater = Instant.parse("2026-10-20T16:00:00Z")
        refusedWith("tool-set-aside") { fulfill(stateWith(placedH1, placedH2, fulfilledH1), h2, muchLater) }
    }

    @Test
    fun `INV-FUL-4 - given Hold Expired is recorded for H1, when H2 is fulfilled, then H2 is set aside`() {
        assertThat(fulfill(stateWith(placedH1, placedH2, fulfilledH1, HoldExpired(h1, now)), h2)).hasSize(1)
    }

    // --- INV-FUL-5 ---

    @Test
    fun `INV-FUL-5 - given 10 October in the shed zone, when fulfilled, then collect by 13 October and fulfilledAt is now`() {
        val event = fulfill(stateWith(placedH1), h1).single()
        assertThat(event.collectByDate).isEqualTo(LocalDate.of(2026, 10, 13))
        assertThat(event.fulfilledAt).isEqualTo(now)
    }

    @Test
    fun `INV-FUL-5 - given 01 30 UTC on 11 October, still 10 October in Denver, when fulfilled, then collect by uses the shed date`() {
        val lateEvening = Instant.parse("2026-10-11T01:30:00Z")
        assertThat(fulfill(stateWith(placedH1), h1, lateEvening).single().collectByDate).isEqualTo(LocalDate.of(2026, 10, 13))
    }

    // --- INV-FUL-6 ---

    @Test
    fun `INV-FUL-6 - given the set-aside expired, when H2 is fulfilled, then the next hold is set aside`() {
        assertThat(fulfill(stateWith(placedH1, placedH2, fulfilledH1, HoldExpired(h1, now)), h2).single().neighborId).isEqualTo(cara)
    }

    @Test
    fun `INV-FUL-6 - given the set-aside holder cancelled, when H2 is fulfilled, then the next hold is set aside`() {
        assertThat(fulfill(stateWith(placedH1, placedH2, fulfilledH1, HoldCancelled(h1, now)), h2)).hasSize(1)
    }

    @Test
    fun `INV-FUL-6 - given the set-aside holder closed their account, when H2 is fulfilled, then H2 is set aside`() {
        assertThat(fulfill(stateWith(placedH1, placedH2, fulfilledH1, NeighborAccountClosed(ben, false, now)), h2)).hasSize(1)
    }

    // --- INV-FUL-8 ---

    @Test
    fun `INV-FUL-8 - given the front holder has an overdue loan on another tool, when H1 is fulfilled, then it is recorded`() {
        val overdue = ToolCheckedOut(LoanId(UUID.randomUUID()), saw, ben, 7, LocalDate.of(2026, 10, 1), now)
        assertThat(fulfill(stateWith(overdue, placedH1), h1)).hasSize(1)
    }

    @Test
    fun `INV-FUL-8 - given the front holder is suspended, when H1 is fulfilled, then it is recorded`() {
        // Suspension is not a fact this decision reads: the same state yields the event.
        assertThat(fulfill(stateWith(placedH1), h1).single().neighborId).isEqualTo(ben)
    }

    // --- INV-FUL-9 ---

    @Test
    fun `INV-FUL-9 - given no hold H9 was placed, when H9 is fulfilled, then refused hold-not-found`() {
        refusedWith("hold-not-found") { fulfill(stateWith(placedH1), h9) }
    }

    // --- INV-FUL-10 ---

    @Test
    fun `INV-FUL-10 - given H1 already fulfilled, when H1 is fulfilled again later, then nothing is recorded`() {
        assertThat(fulfill(stateWith(placedH1, fulfilledH1), h1, Instant.parse("2026-10-11T16:00:00Z"))).isEmpty()
    }

    @Test
    fun `INV-FUL-10 - given H1 fulfilled and since ended, when H1 is fulfilled again, then it still succeeds with nothing recorded`() {
        assertThat(fulfill(stateWith(placedH1, fulfilledH1, HoldExpired(h1, now)), h1)).isEmpty()
    }
}
