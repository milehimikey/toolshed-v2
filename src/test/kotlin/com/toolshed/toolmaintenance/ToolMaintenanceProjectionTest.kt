package com.toolshed.toolmaintenance

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.completetoolmaintenance.ToolMaintenanceCompleted
import com.toolshed.retiretool.ToolRetired
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceScheduled
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceTasksChanged
import com.toolshed.web.Caller
import com.toolshed.web.NOT_SHED_MANAGER
import com.toolshed.web.NOT_SIGNED_IN
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** The view's rules, with the projection's handlers applied in sequence to the in-memory read model, then read. */
class ToolMaintenanceProjectionTest {
    private val nov3 = Instant.parse("2026-11-03T09:00:00Z")
    private val nov3at10 = Instant.parse("2026-11-03T10:00:00Z")
    private val nov3at11 = Instant.parse("2026-11-03T11:00:00Z")
    private val nov3at08 = Instant.parse("2026-11-03T08:00:00Z")
    private val nov4 = Instant.parse("2026-11-04T09:00:00Z")
    private val trimmer = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val ladder = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d2"))
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d3"))
    private val drill2 = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d4"))

    private val repository = ToolMaintenanceRepository()
    private val projection = ToolMaintenanceProjection(repository)

    private fun scheduled(
        tool: ToolId,
        name: String,
        tasks: List<String>,
        notes: String? = null,
        by: String = "Sam",
        at: Instant = nov3,
    ) = ToolMaintenanceScheduled(tool, name, tasks, notes, by, at).also { projection.on(it) }

    private fun changed(
        tool: ToolId,
        tasks: List<String>,
        notes: String? = null,
        by: String = "Sam",
        at: Instant = nov3at11,
    ) = ToolMaintenanceTasksChanged(tool, tasks, notes, by, at).also { projection.on(it) }

    private fun rows() = repository.rows()

    private fun rowOf(tool: ToolId) = rows().single { it.toolId == tool }

    // --- INV-TMNT-1 ---

    @Test
    fun `INV-TMNT-1 given no tools when trimmer scheduled then listed in order`() {
        assertThat(rows()).isEmpty()
        scheduled(trimmer, "Hedge trimmer", listOf("sharpen blades", "oil hinges"))
        val row = rowOf(trimmer)
        assertThat(row.toolName).isEqualTo("Hedge trimmer")
        assertThat(row.tasks).containsExactly("sharpen blades", "oil hinges")
        assertThat(row.scheduledAt).isEqualTo(nov3)
    }

    @Test
    fun `INV-TMNT-1 given hedge trimmer listed when ladder scheduled then both listed`() {
        scheduled(trimmer, "Hedge trimmer", listOf("sharpen blades"))
        scheduled(ladder, "Ladder", listOf("replace rung"))
        assertThat(rows()).hasSize(2)
        assertThat(rowOf(trimmer).tasks).containsExactly("sharpen blades")
        assertThat(rowOf(ladder).tasks).containsExactly("replace rung")
    }

    @Test
    fun `INV-TMNT-1 given one Cordless drill listed when second drill scheduled then both are separate entries`() {
        scheduled(drill, "Cordless drill", listOf("clean"))
        scheduled(drill2, "Cordless drill", listOf("tune"))
        assertThat(rows().map { it.toolId }).containsExactly(drill, drill2)
    }

    @Test
    fun `INV-TMNT-1 given no tools, when scheduled by Sam with a task and notes, then the entry shows them and no lastChanged fields`() {
        scheduled(trimmer, "Hedge trimmer", listOf("sharpen blades"), notes = "blade chipped near the tip", by = "Sam")
        val row = rowOf(trimmer)
        assertThat(row.scheduledBy).isEqualTo("Sam")
        assertThat(row.tasks).containsExactly("sharpen blades")
        assertThat(row.notes).isEqualTo("blade chipped near the tip")
        assertThat(row.lastChangedBy).isNull()
        assertThat(row.lastChangedAt).isNull()
    }

    // --- INV-TMNT-2 ---

    @Test
    fun `INV-TMNT-2 given trimmer and the ladder listed when Tool Maintenance Completed for the trimmer then ladder unchanged`() {
        scheduled(trimmer, "Hedge trimmer", listOf("a"))
        val ladderRow = scheduled(ladder, "Ladder", listOf("b")).let { rowOf(ladder) }
        projection.on(ToolMaintenanceCompleted(trimmer, nov3at10))
        assertThat(rows()).containsExactly(ladderRow)
    }

    @Test
    fun `INV-TMNT-2 given only the ladder listed, when Tool Maintenance Completed for the ladder, then no tools are listed`() {
        scheduled(ladder, "Ladder", listOf("b"))
        projection.on(ToolMaintenanceCompleted(ladder, nov3at10))
        assertThat(rows()).isEmpty()
    }

    // --- INV-TMNT-3 ---

    @Test
    fun `INV-TMNT-3 given trimmer and the ladder listed when Tool Retired for the trimmer then ladder unchanged`() {
        scheduled(trimmer, "Hedge trimmer", listOf("a"))
        scheduled(ladder, "Ladder", listOf("b"))
        val ladderRow = rowOf(ladder)
        projection.on(ToolRetired(trimmer, nov3at10))
        assertThat(rows()).containsExactly(ladderRow)
    }

    @Test
    fun `INV-TMNT-3 INV-TMNT-5 given ladder listed when drill retired not in maintenance then only the ladder is listed`() {
        scheduled(ladder, "Ladder", listOf("b"))
        projection.on(ToolRetired(drill, nov3at10))
        assertThat(rows().map { it.toolId }).containsExactly(ladder)
    }

    // --- INV-TMNT-4 ---

    @Test
    fun `INV-TMNT-4 given trimmer sent, changed, completed when rescheduled later then one fresh entry without lastChanged`() {
        scheduled(trimmer, "Hedge trimmer", listOf("sharpen blades"), notes = "old notes", by = "Sam")
        changed(trimmer, listOf("x"), "n")
        projection.on(ToolMaintenanceCompleted(trimmer, nov3at11))
        scheduled(trimmer, "Hedge trimmer", listOf("replace cord"), by = "Alex", at = nov4)
        val row = rowOf(trimmer)
        assertThat(rows()).hasSize(1)
        assertThat(row.tasks).containsExactly("replace cord")
        assertThat(row.notes).isNull()
        assertThat(row.scheduledBy).isEqualTo("Alex")
        assertThat(row.scheduledAt).isEqualTo(nov4)
        assertThat(row.lastChangedBy).isNull()
        assertThat(row.lastChangedAt).isNull()
    }

    // --- INV-TMNT-6 ---

    @Test
    fun `INV-TMNT-6 given trimmer listed once when Scheduled again then still once`() {
        val event = scheduled(trimmer, "Hedge trimmer", listOf("sharpen blades"))
        projection.on(event)
        assertThat(rows()).hasSize(1)
        assertThat(rowOf(trimmer).tasks).containsExactly("sharpen blades")
        assertThat(rowOf(trimmer).scheduledAt).isEqualTo(nov3)
    }

    @Test
    fun `INV-TMNT-6 given trimmer dropped off after completion when Completed again then unchanged`() {
        scheduled(trimmer, "Hedge trimmer", listOf("a"))
        scheduled(ladder, "Ladder", listOf("b"))
        val done = ToolMaintenanceCompleted(trimmer, nov3at10)
        projection.on(done)
        val after = rows()
        projection.on(done)
        assertThat(rows()).isEqualTo(after)
    }

    @Test
    fun `INV-TMNT-6 a repeated Tool Retired for a tool with no entry changes nothing`() {
        scheduled(ladder, "Ladder", listOf("b"))
        val retired = ToolRetired(trimmer, nov3at10)
        projection.on(retired)
        projection.on(retired)
        assertThat(rows().map { it.toolId }).containsExactly(ladder)
    }

    // --- INV-TMNT-7 ---

    @Test
    fun `INV-TMNT-7 given no tools, when scheduled with clean, tune, clean filter, then the entry shows exactly those three in order`() {
        scheduled(trimmer, "Hedge trimmer", listOf("clean", "tune", "clean filter"))
        assertThat(rowOf(trimmer).tasks).containsExactly("clean", "tune", "clean filter")
    }

    @Test
    fun `INV-TMNT-7 given the trimmer listed with clean, when Tasks Changed to clean and clean, then clean is shown twice`() {
        scheduled(trimmer, "Hedge trimmer", listOf("clean"))
        changed(trimmer, listOf("clean", "clean"))
        assertThat(rowOf(trimmer).tasks).containsExactly("clean", "clean")
    }

    // --- INV-TMNT-8 ---

    @Test
    fun `INV-TMNT-8 given a ladder at 08 00 and drills at 10 00 and 09 00, then the order is drill 09 00, drill 10 00, ladder`() {
        scheduled(ladder, "Ladder", listOf("a"), at = nov3at08)
        scheduled(drill, "cordless drill", listOf("a"), at = nov3at10)
        scheduled(drill2, "Cordless drill", listOf("a"), at = nov3)
        assertThat(rows().map { it.toolId }).containsExactly(drill2, drill, ladder)
    }

    @Test
    fun `INV-TMNT-8 given two tools with the same name and time, then the tool id breaks the tie the same on every read`() {
        scheduled(drill2, "Cordless drill", listOf("a"))
        scheduled(drill, "Cordless drill", listOf("a"))
        assertThat(rows().map { it.toolId }).containsExactly(drill, drill2)
        assertThat(rows().map { it.toolId }).containsExactly(drill, drill2)
    }

    @Test
    fun `INV-TMNT-8 given no tools in maintenance, when listed, then an empty list comes back`() {
        assertThat(rows()).isEmpty()
        assertThat(ToolMaintenanceQueryHandler(repository).handle(ListToolsInMaintenance).tools).isEmpty()
    }

    // --- INV-TMNT-9 and INV-TMNT-10 ---

    @Test
    fun `INV-TMNT-9 given the trimmer listed, when a caller with no sign-in asks, then refused not-signed-in Please sign in`() {
        scheduled(trimmer, "Hedge trimmer", listOf("a"))
        assertThatThrownBy { Caller.ANONYMOUS.requireToolMaintenanceReader() }
            .isInstanceOfSatisfying(CommandRejected::class.java) {
                assertThat(it.invariant).isEqualTo("INV-TMNT-9")
                assertThat(it.reason).isEqualTo(NOT_SIGNED_IN)
                assertThat(it.message).isEqualTo("Please sign in.")
            }
    }

    @Test
    fun `INV-TMNT-10 given a neighbor signed in without the role when asked then refused not-shed-manager`() {
        assertThatThrownBy {
            Caller(neighborId = NeighborId(UUID.randomUUID()), isShedManager = false).requireToolMaintenanceReader()
        }.isInstanceOfSatisfying(CommandRejected::class.java) {
            assertThat(it.invariant).isEqualTo("INV-TMNT-10")
            assertThat(it.reason).isEqualTo(NOT_SHED_MANAGER)
            assertThat(it.message).isEqualTo("Only the shed manager can see the tools in maintenance.")
        }
    }

    @Test
    fun `INV-TMNT-10 the shed manager passes the guard`() {
        Caller(neighborId = null, isShedManager = true).requireToolMaintenanceReader()
    }

    // --- INV-TMNT-11 ---

    @Test
    fun `INV-TMNT-11 given trimmer with one task and notes when Changed to three tasks then replaced as a whole`() {
        scheduled(trimmer, "Hedge trimmer", listOf("sharpen blades"), notes = "blade chipped near the tip", by = "Alex")
        changed(trimmer, listOf("sharpen blades", "replace cord", "oil hinges"), "cord frayed", by = "Sam", at = nov3at11)
        val row = rowOf(trimmer)
        assertThat(row.tasks).containsExactly("sharpen blades", "replace cord", "oil hinges")
        assertThat(row.notes).isEqualTo("cord frayed")
        assertThat(row.lastChangedBy).isEqualTo("Sam")
        assertThat(row.lastChangedAt).isEqualTo(nov3at11)
        assertThat(row.scheduledBy).isEqualTo("Alex")
        assertThat(row.scheduledAt).isEqualTo(nov3)
    }

    @Test
    fun `INV-TMNT-11 given two tasks, when Tasks Changed with sharpen blades only, then only that task is shown`() {
        scheduled(trimmer, "Hedge trimmer", listOf("sharpen blades", "oil hinges"))
        changed(trimmer, listOf("sharpen blades"))
        assertThat(rowOf(trimmer).tasks).containsExactly("sharpen blades")
    }

    @Test
    fun `INV-TMNT-11 given notes on the entry, when Tasks Changed with a task and no notes, then the entry has no notes`() {
        scheduled(trimmer, "Hedge trimmer", listOf("a"), notes = "blade chipped near the tip")
        changed(trimmer, listOf("new task"), notes = null)
        assertThat(rowOf(trimmer).notes).isNull()
        assertThat(rowOf(trimmer).tasks).containsExactly("new task")
    }

    @Test
    fun `INV-TMNT-11 given one change to a and b, when a second change with c, then only c and lastChangedAt is the second time`() {
        scheduled(trimmer, "Hedge trimmer", listOf("z"))
        changed(trimmer, listOf("a", "b"), at = nov3at10)
        changed(trimmer, listOf("c"), at = nov3at11)
        assertThat(rowOf(trimmer).tasks).containsExactly("c")
        assertThat(rowOf(trimmer).lastChangedAt).isEqualTo(nov3at11)
    }

    @Test
    fun `INV-TMNT-11 given the trimmer and the ladder listed, when Tasks Changed for the trimmer, then the ladder's entry is unchanged`() {
        scheduled(trimmer, "Hedge trimmer", listOf("a"))
        scheduled(ladder, "Ladder", listOf("b"))
        val ladderRow = rowOf(ladder)
        changed(trimmer, listOf("c"))
        assertThat(rowOf(ladder)).isEqualTo(ladderRow)
    }

    @Test
    fun `INV-TMNT-11 INV-TMNT-6 given tasks changed to replace cord when Changed again then still the one task`() {
        scheduled(trimmer, "Hedge trimmer", listOf("a"))
        val event = changed(trimmer, listOf("replace cord"))
        val after = rowOf(trimmer)
        projection.on(event)
        assertThat(rowOf(trimmer)).isEqualTo(after)
        assertThat(rowOf(trimmer).tasks).containsExactly("replace cord")
    }

    // --- INV-TMNT-12 ---

    @Test
    fun `INV-TMNT-12 given ladder listed when drill changed not in maintenance then only the ladder is listed`() {
        scheduled(ladder, "Ladder", listOf("b"))
        changed(drill, listOf("x"))
        assertThat(rows().map { it.toolId }).containsExactly(ladder)
    }

    @Test
    fun `INV-TMNT-12 a Tasks Changed replayed after the maintenance ended changes nothing`() {
        scheduled(trimmer, "Hedge trimmer", listOf("a"))
        val event = changed(trimmer, listOf("b"))
        projection.on(ToolMaintenanceCompleted(trimmer, nov3at11))
        projection.on(event)
        assertThat(rows()).isEmpty()
    }

    // --- INV-TMNT-13 ---

    @Test
    fun `INV-TMNT-13 INV-TMNT-6 given trimmer changed to replace cord when 09 00 Scheduled again then both stay`() {
        val original = scheduled(trimmer, "Hedge trimmer", listOf("sharpen blades"), by = "Sam", at = nov3)
        changed(trimmer, listOf("replace cord"))
        projection.on(original)
        val row = rowOf(trimmer)
        assertThat(row.tasks).containsExactly("replace cord")
        assertThat(row.scheduledAt).isEqualTo(nov3)
        assertThat(row.scheduledBy).isEqualTo("Sam")
    }

    @Test
    fun `INV-TMNT-6 INV-TMNT-2 a Scheduled replayed after Completed does not bring the entry back`() {
        val original = scheduled(trimmer, "Hedge trimmer", listOf("sharpen blades"), at = nov3)
        projection.on(ToolMaintenanceCompleted(trimmer, nov3at11))
        projection.on(original)
        assertThat(rows()).isEmpty()
    }

    @Test
    fun `INV-TMNT-6 INV-TMNT-3 a Scheduled replayed after Tool Retired does not bring the entry back`() {
        val original = scheduled(trimmer, "Hedge trimmer", listOf("sharpen blades"), at = nov3)
        projection.on(ToolRetired(trimmer, nov3at11))
        projection.on(original)
        assertThat(rows()).isEmpty()
    }

    @Test
    fun `INV-TMNT-4 INV-TMNT-6 a later Scheduled after Completed still makes a new entry, and an old Completed replay leaves it`() {
        val first = scheduled(trimmer, "Hedge trimmer", listOf("a"), at = nov3)
        val done = ToolMaintenanceCompleted(trimmer, nov3at11)
        projection.on(done)
        scheduled(trimmer, "Hedge trimmer", listOf("b"), at = nov4)
        projection.on(first)
        projection.on(done)
        assertThat(rowOf(trimmer).tasks).containsExactly("b")
    }

    @Test
    fun `INV-TMNT-6 INV-TMNT-4 a Tasks Changed from an earlier maintenance replayed leaves the later entry as it was`() {
        scheduled(trimmer, "Hedge trimmer", listOf("sharpen blades"), at = nov3)
        val firstChange = changed(trimmer, listOf("x"), notes = "n", by = "Sam", at = nov3at11)
        val done = ToolMaintenanceCompleted(trimmer, nov3at11.plusSeconds(3600))
        projection.on(done)
        val second = scheduled(trimmer, "Hedge trimmer", listOf("replace cord"), by = "Alex", at = nov4)
        projection.on(firstChange)
        projection.on(done)
        projection.on(second)
        val row = rowOf(trimmer)
        assertThat(row.tasks).containsExactly("replace cord")
        assertThat(row.notes).isNull()
        assertThat(row.scheduledBy).isEqualTo("Alex")
        assertThat(row.lastChangedBy).isNull()
        assertThat(row.lastChangedAt).isNull()
    }
}
