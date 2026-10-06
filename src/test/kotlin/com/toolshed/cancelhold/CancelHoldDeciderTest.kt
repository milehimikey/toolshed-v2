package com.toolshed.cancelhold

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** The pure decision, with no Axon fixture: each boundary, and the order the first failure is reported in. */
class CancelHoldDeciderTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val handler = CancelHoldCommandHandler(Clock.fixed(now, ZoneOffset.UTC))
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"))
    private val cara = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"))
    private val loan = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))
    private val h1 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"))
    private val h2 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000e2"))

    private fun stateWith(vararg events: Any): CancelHoldCommandHandler.State {
        val state = CancelHoldCommandHandler.State()
        events.forEach { event ->
            when (event) {
                is HoldPlaced -> state.evolve(event)
                is HoldFulfilled -> state.evolve(event)
                is HoldCancelled -> state.evolve(event)
                is HoldExpired -> state.evolve(event)
                is ToolCheckedOut -> state.evolve(event)
                is ToolRetired -> state.evolve(event)
                is ToolLost -> state.evolve(event)
                is NeighborAccountClosed -> state.evolve(event)
                else -> error("unexpected event ${event::class.simpleName}")
            }
        }
        return state
    }

    private fun cancel(
        state: CancelHoldCommandHandler.State,
        by: NeighborId = ben,
        hold: HoldId = h1,
    ) = handler.decide(CancelHold(by, hold), state, now)

    private fun refusedWith(
        reason: String,
        block: () -> Unit,
    ) {
        assertThat(assertThrows<CancelHoldRejected>(block).reason).isEqualTo(reason)
    }

    // --- INV-CNH-2: a waiting hold and a set-aside hold are both cancellable ---

    @Test
    fun `INV-CNH-2 a waiting hold is cancelled`() {
        val recorded = cancel(stateWith(HoldPlaced(h1, drill, ben, now)))
        assertThat(recorded).containsExactly(HoldCancelled(h1, now))
    }

    @Test
    fun `INV-CNH-2 a set-aside hold is cancelled`() {
        val state = stateWith(HoldPlaced(h1, drill, ben, now), HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 17), now))
        assertThat(cancel(state)).containsExactly(HoldCancelled(h1, now))
    }

    // --- INV-CNH-3: each way a hold ends refuses a cancel ---

    @Test
    fun `INV-CNH-3 a hold ended by expiry is refused hold-ended`() {
        refusedWith("hold-ended") { cancel(stateWith(HoldPlaced(h1, drill, ben, now), HoldExpired(h1, now))) }
    }

    @Test
    fun `INV-CNH-3 a hold ended by its holder checking the tool out is refused hold-ended`() {
        val state =
            stateWith(
                HoldPlaced(h1, drill, ben, now),
                ToolCheckedOut(loan, drill, ben, 7, LocalDate.of(2026, 10, 12), now),
            )
        refusedWith("hold-ended") { cancel(state) }
    }

    @Test
    fun `INV-CNH-3 a hold ended by the tool being retired is refused hold-ended`() {
        refusedWith("hold-ended") { cancel(stateWith(HoldPlaced(h1, drill, ben, now), ToolRetired(drill, now))) }
    }

    @Test
    fun `INV-CNH-3 a hold ended by the tool being recorded lost is refused hold-ended`() {
        refusedWith("hold-ended") { cancel(stateWith(HoldPlaced(h1, drill, ben, now), ToolLost(loan, drill, now))) }
    }

    @Test
    fun `INV-CNH-3 a hold ended by the holder closing their account is refused hold-ended`() {
        refusedWith("hold-ended") { cancel(stateWith(HoldPlaced(h1, drill, ben, now), NeighborAccountClosed(ben, false, now))) }
    }

    @Test
    fun `INV-CNH-3 another holder's checkout does not end this hold`() {
        val state =
            stateWith(
                HoldPlaced(h1, drill, ben, now),
                ToolCheckedOut(loan, drill, cara, 7, LocalDate.of(2026, 10, 12), now),
            )
        assertThat(cancel(state)).containsExactly(HoldCancelled(h1, now))
    }

    // --- INV-CNH-8: a repeat records nothing; the order reports ownership before a repeat ---

    @Test
    fun `INV-CNH-8 cancelling an already cancelled hold records nothing`() {
        assertThat(cancel(stateWith(HoldPlaced(h1, drill, ben, now), HoldCancelled(h1, now)))).isEmpty()
    }

    @Test
    fun `INV-CNH-1 a cancelled hold named by someone else is refused not-your-hold before the repeat`() {
        refusedWith("not-your-hold") { cancel(stateWith(HoldPlaced(h1, drill, ben, now), HoldCancelled(h1, now)), by = cara) }
    }

    // --- INV-CNH-7: the hold must exist ---

    @Test
    fun `INV-CNH-7 an unknown hold is refused hold-not-found`() {
        refusedWith("hold-not-found") { cancel(stateWith(HoldPlaced(h2, drill, ben, now)), hold = h1) }
    }

    @Test
    fun `INV-CNH-1 a hold of someone else's is refused not-your-hold`() {
        refusedWith("not-your-hold") { cancel(stateWith(HoldPlaced(h1, drill, ben, now)), by = cara) }
    }
}
