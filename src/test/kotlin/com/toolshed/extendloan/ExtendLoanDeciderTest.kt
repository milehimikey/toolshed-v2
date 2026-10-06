package com.toolshed.extendloan

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** The pure decision, with no Axon fixture: each boundary, and the order the first failure is reported in. */
class ExtendLoanDeciderTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val handler = ExtendLoanCommandHandler(Clock.fixed(now, ZoneOffset.UTC))
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val ladder = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d2"))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"))
    private val l1 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))
    private val hold = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"))
    private val oct12 = LocalDate.of(2026, 10, 12)

    private fun stateWith(vararg events: Any): ExtendLoanCommandHandler.State {
        val state = ExtendLoanCommandHandler.State()
        events.forEach { event ->
            when (event) {
                is ToolAdded -> state.evolve(event)
                is ToolCheckedOut -> state.evolve(event)
                is ToolReturned -> state.evolve(event)
                is ToolLost -> state.evolve(event)
                is ToolRetired -> state.evolve(event)
                is NeighborSuspended -> state.evolve(event)
                is HoldPlaced -> state.evolve(event)
                is HoldCancelled -> state.evolve(event)
                is HoldExpired -> state.evolve(event)
                is HoldFulfilled -> state.evolve(event)
                is NeighborAccountClosed -> state.evolve(event)
                is LoanExtended -> state.evolve(event)
                else -> error("unexpected event ${event::class.simpleName}")
            }
        }
        return state
    }

    private fun drillOnLoan(): Array<Any> =
        arrayOf(ToolAdded(drill, "Cordless drill", null, 7, now), ToolCheckedOut(l1, drill, ana, 7, oct12, now))

    private fun decide(
        state: ExtendLoanCommandHandler.State,
        today: LocalDate = LocalDate.of(2026, 10, 10),
        by: NeighborId = ana,
        from: LocalDate = oct12,
    ) = handler.decide(ExtendLoan(by, l1, from), state, today, now)

    private fun refusedWith(
        reason: String,
        block: () -> Unit,
    ) {
        assertThat(assertThrows<ExtendLoanRejected>(block).reason).isEqualTo(reason)
    }

    // --- INV-EXT-3: the default period, from the current due date ---

    @Test
    fun `INV-EXT-3 the new due date is the current due date plus the tool's default, not the checkout period`() {
        val state = stateWith(ToolAdded(drill, "Drill", null, 7, now), ToolCheckedOut(l1, drill, ana, 3, LocalDate.of(2026, 10, 8), now))
        val recorded = decide(state, today = LocalDate.of(2026, 10, 7), from = LocalDate.of(2026, 10, 8))
        assertThat(recorded.single().dueDate).isEqualTo(LocalDate.of(2026, 10, 15))
    }

    // --- INV-EXT-5: overdue begins the day after the due date ---

    @Test
    fun `INV-EXT-5 on the due date the loan can still be extended`() {
        assertThat(decide(stateWith(*drillOnLoan()), today = oct12)).hasSize(1)
    }

    @Test
    fun `INV-EXT-5 the day after the due date the loan is overdue`() {
        refusedWith("loan-overdue") { decide(stateWith(*drillOnLoan()), today = LocalDate.of(2026, 10, 13)) }
    }

    // --- INV-EXT-4: only holds on this tool count, and ends remove them ---

    @Test
    fun `INV-EXT-4 a hold waiting on a different tool does not block the extension`() {
        val state = stateWith(*drillOnLoan(), ToolAdded(ladder, "Ladder", null, 7, now), HoldPlaced(hold, ladder, ben, now))
        assertThat(decide(state)).hasSize(1)
    }

    @Test
    fun `INV-EXT-4 a cancelled hold no longer blocks the extension`() {
        val state = stateWith(*drillOnLoan(), HoldPlaced(hold, drill, ben, now), HoldCancelled(hold, now))
        assertThat(decide(state)).hasSize(1)
    }

    @Test
    fun `INV-EXT-4 a hold waiting on the tool blocks the extension`() {
        refusedWith("hold-waiting") { decide(stateWith(*drillOnLoan(), HoldPlaced(hold, drill, ben, now))) }
    }

    @Test
    fun `INV-EXT-4 an expired hold no longer blocks the extension`() {
        val state = stateWith(*drillOnLoan(), HoldPlaced(hold, drill, ben, now), HoldExpired(hold, now))
        assertThat(decide(state)).hasSize(1)
    }

    @Test
    fun `INV-EXT-4 a fulfilled hold is no longer waiting, so it does not block the extension`() {
        val state = stateWith(*drillOnLoan(), HoldPlaced(hold, drill, ben, now), HoldFulfilled(hold, drill, ben, oct12, now))
        assertThat(decide(state)).hasSize(1)
    }

    @Test
    fun `INV-EXT-4 a hold ends when its holder checks the tool out, so it does not block that holder's loan`() {
        // Ben's hold is ended by his own checkout of the drill, so his new loan can be extended.
        val benLoan = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000012"))
        val state =
            stateWith(
                ToolAdded(drill, "Cordless drill", null, 7, now),
                HoldPlaced(hold, drill, ben, now),
                ToolCheckedOut(benLoan, drill, ben, 7, oct12, now),
            )
        val recorded = handler.decide(ExtendLoan(ben, benLoan, oct12), state, LocalDate.of(2026, 10, 10), now)
        assertThat(recorded).hasSize(1)
    }

    @Test
    fun `INV-EXT-4 a hold ends when its holder closes their account, so it does not block the extension`() {
        val state = stateWith(*drillOnLoan(), HoldPlaced(hold, drill, ben, now), NeighborAccountClosed(ben, false, now))
        assertThat(decide(state)).hasSize(1)
    }

    // --- rejection order: the first failure is the reason returned ---

    @Test
    fun `INV-EXT-1 a loan that is not the borrower's is reported before a repeat is recognised`() {
        val state = stateWith(*drillOnLoan(), LoanExtended(l1, oct12, LocalDate.of(2026, 10, 19), now))
        refusedWith("not-your-loan") { decide(state, by = ben) }
    }

    @Test
    fun `INV-EXT-10 a repeat from an extended due date is recognised even after the loan ended`() {
        val state =
            stateWith(*drillOnLoan(), LoanExtended(l1, oct12, LocalDate.of(2026, 10, 19), now), ToolReturned(l1, drill, now, oct12, false))
        assertThat(decide(state)).isEmpty()
    }

    @Test
    fun `INV-EXT-2 an ended loan is reported loan-ended before a stale due date`() {
        refusedWith("loan-ended") { decide(stateWith(*drillOnLoan(), ToolLost(l1, drill, now)), from = LocalDate.of(2026, 10, 5)) }
    }

    @Test
    fun `INV-EXT-7 a suspended borrower is reported before an overdue loan`() {
        refusedWith("neighbor-suspended") {
            decide(stateWith(*drillOnLoan(), NeighborSuspended(ana, now)), today = LocalDate.of(2026, 10, 20))
        }
    }

    @Test
    fun `INV-EXT-8 a retired tool is reported before an overdue loan`() {
        refusedWith("tool-retired") { decide(stateWith(*drillOnLoan(), ToolRetired(drill, now)), today = LocalDate.of(2026, 10, 20)) }
    }

    @Test
    fun `INV-EXT-9 an unknown loan is refused loan-not-found`() {
        refusedWith("loan-not-found") { decide(stateWith(ToolAdded(drill, "Drill", null, 7, now))) }
    }
}
