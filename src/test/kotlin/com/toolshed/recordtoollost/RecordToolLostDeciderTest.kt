package com.toolshed.recordtoollost

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.returntool.ToolReturned
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Pure decider tests: one or more per invariant boundary, no framework and no fixture. */
class RecordToolLostDeciderTest {
    private val now: Instant = Instant.parse("2026-10-20T15:00:00Z")
    private val loan = LoanId(UUID.randomUUID())
    private val tool = ToolId(UUID.randomUUID())
    private val ana = NeighborId(UUID.randomUUID())

    private fun state(vararg events: Any): LoanState {
        val s = LoanState()
        events.forEach {
            when (it) {
                is ToolCheckedOut -> s.evolve(it)
                is ToolReturned -> s.evolve(it)
                is ToolLost -> s.evolve(it)
                else -> error("no handler for ${it::class.simpleName}")
            }
        }
        return s
    }

    private fun checkedOut() = ToolCheckedOut(loan, tool, ana, 7, LocalDate.of(2026, 10, 12), now)

    private fun decide(s: LoanState) = RecordToolLostDecider.decide(RecordToolLost(loan, tool), s, now)

    private fun refusal(block: () -> Unit): RecordToolLostRejected = assertFailsWith<RecordToolLostRejected>(block = block)

    @Test
    fun `INV-LOST-2 - a tool out on loan is recorded lost against that loan, carrying the loan's tool`() {
        assertEquals(listOf(ToolLost(loan, tool, now)), decide(state(checkedOut())))
    }

    @Test
    fun `INV-LOST-3 - recording a loss records only Tool Lost`() {
        val events = decide(state(checkedOut()))
        assertEquals(1, events.size)
        assertTrue(events.all { it is ToolLost })
    }

    @Test
    fun `INV-LOST-11 - a toolId that is not the loan's tool names no loan, refused loan-not-found`() {
        val e = refusal { RecordToolLostDecider.decide(RecordToolLost(loan, ToolId(UUID.randomUUID())), state(checkedOut()), now) }
        assertEquals("loan-not-found", e.reason)
    }

    @Test
    fun `INV-LOST-11 - a loan that was never checked out is refused loan-not-found`() {
        val e = refusal { decide(state()) }
        assertEquals("loan-not-found", e.reason)
        assertEquals("INV-LOST-11", e.invariant)
    }

    @Test
    fun `INV-LOST-2 - a loan already returned is refused loan-ended`() {
        val s = state(checkedOut(), ToolReturned(loan, tool, now, LocalDate.of(2026, 10, 20), false))
        val e = refusal { decide(s) }
        assertEquals("loan-ended", e.reason)
        assertEquals("INV-LOST-2", e.invariant)
    }

    @Test
    fun `INV-LOST-10 - a loan already recorded lost records nothing`() {
        val s = state(checkedOut(), ToolLost(loan, tool, now))
        assertTrue(decide(s).isEmpty())
    }

    @Test
    fun `INV-LOST-2 - a tool retired while out can still be recorded lost, because the loan is still out`() {
        // Retirement is not a fact this decision reads; only the loan's own events are.
        assertEquals(1, decide(state(checkedOut())).size)
    }
}
