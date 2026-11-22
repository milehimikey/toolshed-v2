package com.toolshed.retiretool

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.ToolId
import com.toolshed.recordtoollost.ToolLost
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/** The pure decision, with no Axon fixture: the order of the first failure, and the repeat rule. */
class RetireToolDeciderTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val ladder = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val unknown = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"))
    private val loan = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))

    private fun stateWith(vararg events: Any): ToolState {
        val state = ToolState()
        events.forEach { event ->
            when (event) {
                is ToolAdded -> state.evolve(event)
                is ToolRetired -> state.evolve(event)
                is ToolLost -> state.evolve(event)
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
