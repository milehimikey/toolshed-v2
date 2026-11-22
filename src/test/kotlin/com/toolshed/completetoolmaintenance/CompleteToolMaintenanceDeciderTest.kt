package com.toolshed.completetoolmaintenance

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.ToolId
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceScheduled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/** The pure decision, with no Axon fixture: the order of the first failure, and the repeat rule. */
class CompleteToolMaintenanceDeciderTest {
    private val now = Instant.parse("2026-11-20T09:00:00Z")
    private val trimmer = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val unknown = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"))
    private val loan = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))

    private val added = ToolAdded(trimmer, "Hedge trimmer", null, 7, now)
    private val scheduled = ToolMaintenanceScheduled(trimmer, "Hedge trimmer", listOf("sharpen"), null, "Mike", now)

    private fun stateWith(vararg events: Any): MaintenanceState {
        val state = MaintenanceState()
        events.forEach { event ->
            when (event) {
                is ToolAdded -> state.evolve(event)
                is ToolMaintenanceScheduled -> state.evolve(event)
                is ToolMaintenanceCompleted -> state.evolve(event)
                is ToolRetired -> state.evolve(event)
                is ToolLost -> state.evolve(event)
                else -> error("unexpected event ${event::class.simpleName}")
            }
        }
        return state
    }

    private fun decide(state: MaintenanceState) = CompleteToolMaintenanceDecider.decide(CompleteToolMaintenance(trimmer), state, now)

    // --- INV-CTMN-5: a tool in maintenance is completed, recording only the completion ---

    @Test
    fun `INV-CTMN-5 a tool in maintenance records Tool Maintenance Completed`() {
        assertThat(decide(stateWith(added, scheduled))).containsExactly(ToolMaintenanceCompleted(trimmer, now))
    }

    @Test
    fun `INV-CTMN-5 a tool scheduled again after completing is in maintenance again`() {
        val state = stateWith(added, scheduled, ToolMaintenanceCompleted(trimmer, now), scheduled)
        assertThat(decide(state)).containsExactly(ToolMaintenanceCompleted(trimmer, now))
    }

    // --- INV-CTMN-2: a tool not in maintenance records nothing ---

    @Test
    fun `INV-CTMN-2 a tool already completed records nothing`() {
        assertThat(decide(stateWith(added, scheduled, ToolMaintenanceCompleted(trimmer, now)))).isEmpty()
    }

    @Test
    fun `INV-CTMN-2 a tool never sent for maintenance records nothing`() {
        assertThat(decide(stateWith(added))).isEmpty()
    }

    // --- INV-CTMN-3: the tool must exist, reported before the repeat ---

    @Test
    fun `INV-CTMN-3 an unknown tool is refused tool-not-found`() {
        val refused = assertThrows<CompleteToolMaintenanceRejected> { decide(stateWith()) }
        assertThat(refused.reason).isEqualTo("tool-not-found")
        assertThat(refused.invariant).isEqualTo("INV-CTMN-3")
        assertThat(refused.message).isEqualTo("There is no such tool.")
        assertThat(unknown).isNotEqualTo(trimmer)
    }

    // --- INV-CTMN-4: a retired tool is refused, even one that was in maintenance, before the repeat rule ---

    @Test
    fun `INV-CTMN-4 a tool retired while in maintenance is refused tool-retired`() {
        val refused = assertThrows<CompleteToolMaintenanceRejected> { decide(stateWith(added, scheduled, ToolRetired(trimmer, now))) }
        assertThat(refused.reason).isEqualTo("tool-retired")
        assertThat(refused.invariant).isEqualTo("INV-CTMN-4")
        assertThat(refused.message).isEqualTo("That tool has been retired.")
    }

    @Test
    fun `INV-CTMN-4 a tool recorded lost is refused tool-retired`() {
        val refused = assertThrows<CompleteToolMaintenanceRejected> { decide(stateWith(added, ToolLost(loan, trimmer, now))) }
        assertThat(refused.reason).isEqualTo("tool-retired")
    }

    @Test
    fun `INV-CTMN-4 a retired tool that was never in maintenance is still refused rather than succeeding quietly`() {
        assertThrows<CompleteToolMaintenanceRejected> { decide(stateWith(added, ToolRetired(trimmer, now))) }
    }
}
