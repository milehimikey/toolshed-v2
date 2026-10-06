package com.toolshed.placehold

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
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
class PlaceHoldDeciderTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val handler = PlaceHoldCommandHandler(Clock.fixed(now, ZoneOffset.UTC))
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val ladder = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d2"))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"))
    private val cleo = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"))
    private val loan = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))
    private val h1 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"))
    private val h2 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e2"))

    private fun stateWith(vararg events: Any): PlaceHoldCommandHandler.State {
        val state = PlaceHoldCommandHandler.State()
        events.forEach { event ->
            when (event) {
                is ToolAdded -> state.evolve(event)
                is ToolRetired -> state.evolve(event)
                is ToolLost -> state.evolve(event)
                is ToolCheckedOut -> state.evolve(event)
                is ToolReturned -> state.evolve(event)
                is NeighborRegistered -> state.evolve(event)
                is NeighborSuspended -> state.evolve(event)
                is NeighborAccountClosed -> state.evolve(event)
                is HoldPlaced -> state.evolve(event)
                is HoldFulfilled -> state.evolve(event)
                is HoldCancelled -> state.evolve(event)
                else -> error("unexpected event ${event::class.simpleName}")
            }
        }
        return state
    }

    /** The shed with the drill on loan to Ana, and Ben and Cleo registered. */
    private fun shed(vararg more: Any): Array<Any> =
        arrayOf(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            NeighborRegistered(ben, "Ben Cole", "ben@example.org", now),
            NeighborRegistered(cleo, "Cleo Park", "cleo@example.org", now),
            ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
            *more,
        )

    private fun decide(
        state: PlaceHoldCommandHandler.State,
        hold: HoldId = h2,
        tool: ToolId = drill,
        by: NeighborId = cleo,
    ) = handler.decide(PlaceHold(hold, tool, by), state, now)

    private fun refusedWith(
        reason: String,
        block: () -> Unit,
    ) {
        assertThat(assertThrows<PlaceHoldRejected>(block).reason).isEqualTo(reason)
    }

    // --- INV-HOLD-2: the three states that make a tool unavailable ---

    @Test
    fun `INV-HOLD-2 a tool returned with no holds waiting is available and refused`() {
        val state = stateWith(*shed(ToolReturned(loan, drill, now, LocalDate.of(2026, 10, 10), false)))
        refusedWith("tool-available") { decide(state) }
    }

    @Test
    fun `INV-HOLD-2 a tool set aside for a holder is not available`() {
        val state =
            stateWith(
                *shed(
                    HoldPlaced(h1, drill, ben, now),
                    ToolReturned(loan, drill, now, LocalDate.of(2026, 10, 10), false),
                    HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 13), now),
                ),
            )
        assertThat(decide(state)).hasSize(1)
    }

    @Test
    fun `INV-HOLD-2 a tool with a hold waiting and no loan is not available`() {
        val state =
            stateWith(
                ToolAdded(drill, "Cordless drill", null, 7, now),
                NeighborRegistered(cleo, "Cleo Park", "cleo@example.org", now),
                NeighborRegistered(ben, "Ben Cole", "ben@example.org", now),
                HoldPlaced(h1, drill, ben, now),
            )
        assertThat(decide(state)).hasSize(1)
    }

    // --- INV-HOLD-3: a lost tool is retired ---

    @Test
    fun `INV-HOLD-3 a tool recorded lost refuses holds as retired`() {
        refusedWith("tool-retired") { decide(stateWith(*shed(ToolLost(loan, drill, now)))) }
    }

    // --- INV-HOLD-7: a hold that has ended no longer counts ---

    @Test
    fun `INV-HOLD-7 a cancelled hold no longer makes the neighbor already holding`() {
        val state = stateWith(*shed(HoldPlaced(h1, drill, cleo, now), HoldCancelled(h1, now)))
        assertThat(decide(state)).hasSize(1)
    }

    @Test
    fun `INV-HOLD-7 a neighbor's waiting hold on the tool makes them already holding`() {
        refusedWith("already-holding") { decide(stateWith(*shed(HoldPlaced(h1, drill, cleo, now))), hold = h2, by = cleo) }
    }

    // --- INV-HOLD-8: on loan to the neighbor is reported before already holding ---

    @Test
    fun `INV-HOLD-8 the borrower's own loan is reported before any hold rule`() {
        refusedWith("tool-on-loan-to-you") { decide(stateWith(*shed()), by = ana) }
    }

    // --- rejection order: the first failure is the reason returned ---

    @Test
    fun `INV-HOLD-13 a repeat is recognised before the tool is checked against the shed`() {
        val state = stateWith(*shed(HoldPlaced(h1, drill, ben, now)))
        assertThat(decide(state, hold = h1, by = ben)).isEmpty()
    }

    @Test
    fun `INV-HOLD-14 an unknown tool is reported before an unknown neighbor`() {
        val unknownTool = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"))
        val unknownNeighbor = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000fe"))
        refusedWith("tool-not-found") { decide(stateWith(), tool = unknownTool, by = unknownNeighbor) }
    }

    @Test
    fun `INV-HOLD-3 a retired tool is reported before a closed account`() {
        val state = stateWith(*shed(ToolRetired(drill, now), NeighborAccountClosed(cleo, false, now)))
        refusedWith("tool-retired") { decide(state) }
    }

    @Test
    fun `INV-HOLD-9 a suspended neighbor is reported before the tool on loan rule`() {
        refusedWith("neighbor-suspended") { decide(stateWith(*shed(NeighborSuspended(ana, now))), by = ana) }
    }

    @Test
    fun `INV-HOLD-2 a free ladder with no holds is refused available`() {
        val state = stateWith(ToolAdded(ladder, "Ladder", null, 7, now), NeighborRegistered(ben, "Ben Cole", "ben@example.org", now))
        refusedWith("tool-available") { decide(state, tool = ladder, by = ben) }
    }
}
