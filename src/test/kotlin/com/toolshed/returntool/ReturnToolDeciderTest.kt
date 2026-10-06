package com.toolshed.returntool

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.recordtoollost.ToolLost
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The pure decision, with no Axon fixture: the lateness boundary, the repeat, and the order of the first failure. */
class ReturnToolDeciderTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"))
    private val loan = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))
    private val oct12 = LocalDate.of(2026, 10, 12)

    private fun stateWith(vararg events: Any): LoanBookState {
        val state = LoanBookState()
        events.forEach { event ->
            when (event) {
                is ToolCheckedOut -> state.evolve(event)
                is LoanExtended -> state.evolve(event)
                is ToolReturned -> state.evolve(event)
                is ToolLost -> state.evolve(event)
                else -> error("unexpected event ${event::class.simpleName}")
            }
        }
        return state
    }

    private fun checkedOut() = ToolCheckedOut(loan, drill, ana, 7, oct12, now)

    private fun decide(
        state: LoanBookState,
        today: LocalDate,
        by: NeighborId = ana,
    ) = ReturnToolDecider.decide(ReturnTool(by, loan), state, today, now)

    private fun refusedWith(
        reason: String,
        block: () -> Unit,
    ) {
        assertThat(assertThrows<ReturnToolRejected>(block).reason).isEqualTo(reason)
    }

    // --- INV-RET-4: late from the day after the due date, judged against the due date in force ---

    @Test
    fun `INV-RET-4 a return on the due date is on time`() {
        assertThat(decide(stateWith(checkedOut()), oct12).single().late).isFalse()
    }

    @Test
    fun `INV-RET-4 a return on the day after the due date is late`() {
        assertThat(decide(stateWith(checkedOut()), LocalDate.of(2026, 10, 13)).single().late).isTrue()
    }

    @Test
    fun `INV-RET-4 an extension moves the due date that lateness is judged against`() {
        val state = stateWith(checkedOut(), LoanExtended(loan, oct12, LocalDate.of(2026, 10, 19), now))
        assertThat(decide(state, LocalDate.of(2026, 10, 18)).single().late).isFalse()
    }

    @Test
    fun `INV-RET-4 the returned date is the shed day given to the decider`() {
        assertThat(decide(stateWith(checkedOut()), LocalDate.of(2026, 10, 11)).single().returnedOn).isEqualTo(LocalDate.of(2026, 10, 11))
    }

    // --- INV-RET-10: a repeat records nothing ---

    @Test
    fun `INV-RET-10 a loan already returned records nothing`() {
        val state = stateWith(checkedOut(), ToolReturned(loan, drill, now, oct12, false))
        assertThat(decide(state, oct12)).isEmpty()
    }

    // --- INV-RET-1 and INV-RET-2: order of the first failure ---

    @Test
    fun `INV-RET-1 another borrower is refused not-your-loan before the repeat is recognised`() {
        val state = stateWith(checkedOut(), ToolReturned(loan, drill, now, oct12, false))
        refusedWith("not-your-loan") { decide(state, oct12, by = ben) }
    }

    @Test
    fun `INV-RET-2 a lost loan is refused loan-ended`() {
        refusedWith("loan-ended") { decide(stateWith(checkedOut(), ToolLost(loan, drill, now)), oct12) }
    }

    @Test
    fun `INV-RET-11 an unknown loan is refused loan-not-found`() {
        refusedWith("loan-not-found") { decide(stateWith(), oct12) }
    }

    @Test
    fun `INV-RET-10 a repeat is recognised even after the tool was retired`() {
        // Retirement does not end a loan, so a return recorded earlier still repeats cleanly.
        val state = stateWith(checkedOut(), ToolReturned(loan, drill, now, oct12, false))
        assertThat(decide(state, oct12)).isEmpty()
    }
}
