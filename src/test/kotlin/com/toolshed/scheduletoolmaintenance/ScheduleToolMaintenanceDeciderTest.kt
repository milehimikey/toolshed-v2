package com.toolshed.scheduletoolmaintenance

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.ToolId
import com.toolshed.retiretool.ToolRetired
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/** The pure decision, with no Axon fixture: the order of the first failure and the tidying boundaries. */
class ScheduleToolMaintenanceDeciderTest {
    private val now = Instant.parse("2026-11-20T10:00:00Z")
    private val ladder = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val unknown = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"))

    private fun stateWith(vararg events: Any): ScheduleMaintenanceState {
        val state = ScheduleMaintenanceState()
        events.forEach { event ->
            when (event) {
                is ToolAdded -> state.evolve(event)
                is ToolRetired -> state.evolve(event)
                is ToolMaintenanceScheduled -> state.evolve(event)
                else -> error("unexpected event ${event::class.simpleName}")
            }
        }
        return state
    }

    private val free = stateWith(ToolAdded(ladder, "Ladder", null, 7, now))

    private fun decide(
        tasks: List<String>?,
        notes: String? = null,
        tool: ToolId = ladder,
        state: ScheduleMaintenanceState = free,
        name: String? = "Dana",
    ) = ScheduleToolMaintenanceDecider.decide(ScheduleToolMaintenance(tool, tasks, notes, name), state, now)

    private fun reasonOf(block: () -> Unit): String = assertThrows<ScheduleToolMaintenanceRejected> { block() }.reason

    // --- INV-STM-2: tidy first, then the limits apply to what is left ---

    @Test
    fun `INV-STM-2 a missing task list is tasks-missing`() {
        assertThat(reasonOf { decide(null) }).isEqualTo("tasks-missing")
    }

    @Test
    fun `INV-STM-2 repeats and blanks do not count towards the ten-task limit`() {
        val tasks = (1..10).map { "task $it" } + listOf("TASK 1", " ", "")
        val recorded = decide(tasks).single() as ToolMaintenanceScheduled
        assertThat(recorded.tasks).hasSize(10)
    }

    @Test
    fun `INV-STM-2 a task is trimmed of Unicode whitespace before its length is checked`() {
        val tasks = listOf(" " + "x".repeat(1000) + " ")
        val recorded = decide(tasks).single() as ToolMaintenanceScheduled
        assertThat(recorded.tasks.single()).hasSize(1000)
    }

    @Test
    fun `INV-STM-2 too many tasks is refused before a too-long task`() {
        val tasks = (1..11).map { "$it".padEnd(1001, 'x') }
        assertThat(reasonOf { decide(tasks) }).isEqualTo("too-many-tasks")
    }

    // --- INV-STM-11: notes are trimmed, and checked after the tasks and before the tool ---

    @Test
    fun `INV-STM-11 notes too long are refused before an unknown tool`() {
        assertThat(reasonOf { decide(listOf("a"), notes = "n".repeat(5001), tool = unknown) }).isEqualTo("notes-too-long")
    }

    @Test
    fun `INV-STM-11 tasks are checked before notes`() {
        assertThat(reasonOf { decide(emptyList(), notes = "n".repeat(5001)) }).isEqualTo("tasks-missing")
    }

    @Test
    fun `INV-STM-11 notes are trimmed and padded notes just inside the limit are accepted`() {
        val recorded = decide(listOf("a"), notes = " " + "n".repeat(5000) + " ").single() as ToolMaintenanceScheduled
        assertThat(recorded.notes).hasSize(5000)
    }

    // --- INV-STM-3, INV-STM-4: existence, then retirement, before anything else about the tool ---

    @Test
    fun `INV-STM-3 an unknown tool is tool-not-found`() {
        assertThat(reasonOf { decide(listOf("a"), tool = unknown) }).isEqualTo("tool-not-found")
    }

    @Test
    fun `INV-STM-4 a retired tool in maintenance is refused tool-retired, not edited`() {
        val state =
            stateWith(
                ToolAdded(ladder, "Ladder", null, 7, now),
                ToolMaintenanceScheduled(ladder, "Ladder", listOf("a"), null, "Dana", now),
                ToolRetired(ladder, now),
            )
        assertThat(reasonOf { decide(listOf("a"), state = state) }).isEqualTo("tool-retired")
    }

    // --- INV-STM-8 / INV-STM-12: the edit and the unchanged request ---

    @Test
    fun `INV-STM-12 both notes absent counts as equal, so nothing is recorded`() {
        val state =
            stateWith(
                ToolAdded(ladder, "Ladder", null, 7, now),
                ToolMaintenanceScheduled(ladder, "Ladder", listOf("a"), null, "Dana", now),
            )
        assertThat(decide(listOf(" a "), notes = "  ", state = state)).isEmpty()
    }

    @Test
    fun `INV-STM-8 tasks equal only apart from case count as a change`() {
        val state =
            stateWith(
                ToolAdded(ladder, "Ladder", null, 7, now),
                ToolMaintenanceScheduled(ladder, "Ladder", listOf("Oil hinges"), null, "Dana", now),
            )
        assertThat(decide(listOf("oil hinges"), state = state).single()).isInstanceOf(ToolMaintenanceTasksChanged::class.java)
    }

    // --- INV-STM-10: who acted comes from sign-in alone ---

    @Test
    fun `INV-STM-10 no name is refused not-signed-in and the name is recorded trimmed`() {
        assertThat(reasonOf { decide(listOf("a"), name = null) }).isEqualTo("not-signed-in")
        assertThat((decide(listOf("a"), name = " Dana ").single() as ToolMaintenanceScheduled).scheduledBy).isEqualTo("Dana")
    }
}
