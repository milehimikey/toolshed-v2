package com.toolshed.retiretool

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.expirehold.HoldExpired
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The pure decision, with no Axon fixture: the order of the first failure, and the repeat rule. */
class RetireToolDeciderTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val ladder = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val unknown = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"))
    private val loan = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b2"))
    private val cara = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b3"))
    private val other = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"))
    private val h1 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"))
    private val h2 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000c2"))
    private val h3 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000c3"))
    private val h4 = HoldId(UUID.fromString("00000000-0000-0000-0000-0000000000c4"))

    private fun stateWith(vararg events: Any): ToolState {
        val state = ToolState()
        events.forEach { event ->
            when (event) {
                is ToolAdded -> state.evolve(event)
                is ToolRetired -> state.evolve(event)
                is ToolLost -> state.evolve(event)
                is HoldPlaced -> state.evolve(event)
                is HoldCancelled -> state.evolve(event)
                is HoldExpired -> state.evolve(event)
                is ToolCheckedOut -> state.evolve(event)
                else -> error("unexpected event ${event::class.simpleName}")
            }
        }
        return state
    }

    // --- INV-RTR-6: the tool's loans and holds never refuse a retirement; only its existence matters here ---

    @Test
    fun `INV-RTR-6 an added tool that is in use is retired`() {
        val recorded = RetireToolDecider.decide(RetireTool(ladder), stateWith(ToolAdded(ladder, "Ladder", null, 7, now)), now)
        assertThat(recorded).containsExactly(ToolRetired(ladder, now))
    }

    @Test
    fun `INV-RTR-6 every live hold on the tool is cancelled with it, but no ended hold and no other tool's hold`() {
        val state =
            stateWith(
                ToolAdded(ladder, "Ladder", null, 7, now),
                HoldPlaced(h1, ladder, ana, now),
                HoldPlaced(h2, ladder, ben, now),
                HoldPlaced(h3, ladder, cara, now),
                HoldPlaced(h4, other, ana, now),
                HoldCancelled(h2, now),
            )
        val recorded = RetireToolDecider.decide(RetireTool(ladder), state, now)
        assertThat(recorded).containsExactly(ToolRetired(ladder, now), HoldCancelled(h1, now), HoldCancelled(h3, now))
    }

    @Test
    fun `INV-RTR-6 a hold its holder's checkout ended is not cancelled again`() {
        val state =
            stateWith(
                ToolAdded(ladder, "Ladder", null, 7, now),
                HoldPlaced(h1, ladder, ana, now),
                ToolCheckedOut(loan, ladder, ana, 7, LocalDate.of(2026, 10, 13), now),
            )
        assertThat(RetireToolDecider.decide(RetireTool(ladder), state, now)).containsExactly(ToolRetired(ladder, now))
    }

    @Test
    fun `INV-RTR-6 an expired hold is not cancelled`() {
        val state = stateWith(ToolAdded(ladder, "Ladder", null, 7, now), HoldPlaced(h1, ladder, ana, now), HoldExpired(h1, now))
        assertThat(RetireToolDecider.decide(RetireTool(ladder), state, now)).containsExactly(ToolRetired(ladder, now))
    }

    // --- INV-RTR-2: a repeat records nothing, including a tool retired by being recorded lost ---

    @Test
    fun `INV-RTR-2 a tool already retired records nothing`() {
        val state = stateWith(ToolAdded(ladder, "Ladder", null, 7, now), ToolRetired(ladder, now))
        assertThat(RetireToolDecider.decide(RetireTool(ladder), state, now)).isEmpty()
    }

    @Test
    fun `INV-RTR-2 a tool recorded lost, which retired it, records nothing`() {
        val state = stateWith(ToolAdded(ladder, "Ladder", null, 7, now), ToolLost(loan, ladder, now))
        assertThat(RetireToolDecider.decide(RetireTool(ladder), state, now)).isEmpty()
    }

    // --- INV-RTR-10: the tool must exist, and that is reported before the repeat ---

    @Test
    fun `INV-RTR-10 an unknown tool is refused tool-not-found`() {
        val refused = assertThrows<RetireToolRejected> { RetireToolDecider.decide(RetireTool(unknown), stateWith(), now) }
        assertThat(refused.reason).isEqualTo("tool-not-found")
        assertThat(refused.invariant).isEqualTo("INV-RTR-10")
    }
}
