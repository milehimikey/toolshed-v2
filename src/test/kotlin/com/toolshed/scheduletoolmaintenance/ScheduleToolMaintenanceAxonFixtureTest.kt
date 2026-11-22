package com.toolshed.scheduletoolmaintenance

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.completetoolmaintenance.ToolMaintenanceCompleted
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.web.Caller
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.AxonTestPhase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** One test per scenario in slices/schedule-tool-maintenance.md, built from the slice's own configure function. */
class ScheduleToolMaintenanceAxonFixtureTest {
    private val now = Instant.parse("2026-11-20T10:00:00Z")
    private val earlier = Instant.parse("2026-11-19T10:00:00Z")
    private val ladder = ToolId(UUID.nameUUIDFromBytes("ladder".toByteArray()))
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val trimmer = ToolId(UUID.nameUUIDFromBytes("trimmer".toByteArray()))
    private val t9 = ToolId(UUID.nameUUIDFromBytes("t9".toByteArray()))
    private val ana = NeighborId(UUID.nameUUIDFromBytes("ana".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("ben".toByteArray()))
    private val cara = NeighborId(UUID.nameUUIDFromBytes("cara".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val h1 = HoldId(UUID.nameUUIDFromBytes("h1".toByteArray()))
    private val h2 = HoldId(UUID.nameUUIDFromBytes("h2".toByteArray()))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        fixture = AxonTestFixture.with(ScheduleToolMaintenanceConfiguration.configure(EventSourcingConfigurer.create(), clock))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    private fun shed(): List<Any> =
        listOf(
            ToolAdded(ladder, "Ladder", null, 7, earlier),
            ToolAdded(drill, "Cordless drill", null, 7, earlier),
            ToolAdded(trimmer, "Hedge trimmer", null, 7, earlier),
        )

    private fun schedule(
        tool: ToolId,
        vararg tasks: String,
        notes: String? = null,
        name: String? = "Dana",
    ) = ScheduleToolMaintenance(tool, tasks.toList(), notes, name)

    private fun scheduled(
        tool: ToolId,
        name: String,
        tasks: List<String>,
        notes: String? = null,
        by: String = "Dana",
        at: Instant = earlier,
    ) = ToolMaintenanceScheduled(tool, name, tasks, notes, by, at)

    private fun assertRejected(
        ex: Throwable,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        val rejected =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<ScheduleToolMaintenanceRejected>()
                .firstOrNull()
        requireNotNull(rejected) { "no ScheduleToolMaintenanceRejected in the exception chain: $ex" }
        assertThat(rejected.invariant).isEqualTo(invariant)
        assertThat(rejected.reason).isEqualTo(reason)
        assertThat(rejected.message).isEqualTo(sentence)
    }

    private fun refusedWith(
        events: List<Any>,
        command: ScheduleToolMaintenance,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        fixture
            .given()
            .recorded(events)
            .`when`()
            .command(command)
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, invariant, reason, sentence) }
    }

    private fun recordedAs(
        events: List<Any>,
        command: ScheduleToolMaintenance,
        vararg expected: Any,
    ) {
        fixture
            .given()
            .recorded(events)
            .`when`()
            .command(command)
            .then()
            .success()
            .events(*expected)
    }

    @Test
    fun `INV-STM-5 INV-STM-6 INV-STM-7 given the drill is free, when Dana schedules it, then Tool Maintenance Scheduled is recorded`() {
        recordedAs(
            shed(),
            schedule(drill, "Sharpen bits", "Clean chuck", notes = "Chuck sticks when cold"),
            ToolMaintenanceScheduled(drill, "Cordless drill", listOf("Sharpen bits", "Clean chuck"), "Chuck sticks when cold", "Dana", now),
        )
    }

    @Test
    fun `INV-STM-2 given the ladder is free, when tasks have blanks and repeats, then they are tidied`() {
        recordedAs(
            shed(),
            schedule(ladder, "  Oil hinges ", "", "oil hinges", "Check rungs"),
            ToolMaintenanceScheduled(ladder, "Ladder", listOf("Oil hinges", "Check rungs"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-2 given the ladder is free, when the only task is blank, then refused tasks-missing`() {
        refusedWith(shed(), schedule(ladder, "   "), "INV-STM-2", "tasks-missing", "Say what work the tool needs.")
    }

    @Test
    fun `INV-STM-11 given the ladder is free, when notes are blank, then scheduled with no notes`() {
        recordedAs(
            shed(),
            schedule(ladder, "Oil hinges", notes = "   "),
            ToolMaintenanceScheduled(ladder, "Ladder", listOf("Oil hinges"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-11 given the ladder is free, when notes are 5001 characters, then refused notes-too-long`() {
        refusedWith(
            shed(),
            schedule(ladder, "Oil hinges", notes = "n".repeat(5001)),
            "INV-STM-11",
            "notes-too-long",
            "Notes can be at most 5000 characters.",
        )
    }

    @Test
    fun `INV-STM-11 given the ladder is free, when notes are exactly 5000 characters, then scheduled with all the notes`() {
        val notes = "n".repeat(5000)
        recordedAs(
            shed(),
            schedule(ladder, "Oil hinges", notes = notes),
            ToolMaintenanceScheduled(ladder, "Ladder", listOf("Oil hinges"), notes, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-10 given Dana is signed in, when the request also names someone else, then scheduledBy is Dana`() {
        // The command has no scheduledBy to send; the signed-in name is the only source. The REST test shows a body value is ignored.
        recordedAs(
            shed(),
            schedule(ladder, "Oil hinges", name = "  Dana "),
            ToolMaintenanceScheduled(ladder, "Ladder", listOf("Oil hinges"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-10 given the shed manager has no name, when they schedule, then refused not-signed-in`() {
        refusedWith(shed(), schedule(ladder, "Oil hinges", name = "  "), "INV-STM-10", "not-signed-in", "Please sign in.")
    }

    @Test
    fun `INV-STM-2 given the ladder is free, when 11 different tasks are sent, then refused too-many-tasks`() {
        refusedWith(
            shed(),
            schedule(ladder, *(1..11).map { "task $it" }.toTypedArray()),
            "INV-STM-2",
            "too-many-tasks",
            "A tool can have at most 10 tasks.",
        )
    }

    @Test
    fun `INV-STM-2 given the ladder is free, when 10 tasks of 1000 characters are sent, then all 10 are scheduled`() {
        val tasks = (0 until 10).map { "$it".padEnd(1000, 'x') }
        recordedAs(shed(), schedule(ladder, *tasks.toTypedArray()), ToolMaintenanceScheduled(ladder, "Ladder", tasks, null, "Dana", now))
    }

    @Test
    fun `INV-STM-2 given the ladder is free, when a task is 1001 characters, then refused task-too-long`() {
        refusedWith(
            shed(),
            schedule(ladder, "x".repeat(1001)),
            "INV-STM-2",
            "task-too-long",
            "A task can be at most 1000 characters.",
        )
    }

    @Test
    fun `INV-STM-3 given no tool T9 has been added, when the shed manager schedules T9, then refused tool-not-found`() {
        refusedWith(shed(), schedule(t9, "Sharpen"), "INV-STM-3", "tool-not-found", "There is no such tool.")
    }

    @Test
    fun `INV-STM-4 given the hedge trimmer is retired, when scheduled, then refused tool-retired`() {
        refusedWith(
            shed() + ToolRetired(trimmer, earlier),
            schedule(trimmer, "Sharpen"),
            "INV-STM-4",
            "tool-retired",
            "That tool has been retired.",
        )
    }

    @Test
    fun `INV-STM-4 given the trimmer was in maintenance and then retired, when scheduled, then refused tool-retired`() {
        refusedWith(
            shed() + scheduled(trimmer, "Hedge trimmer", listOf("Sharpen")) + ToolRetired(trimmer, earlier),
            schedule(trimmer, "Sharpen"),
            "INV-STM-4",
            "tool-retired",
            "That tool has been retired.",
        )
    }

    @Test
    fun `INV-STM-5 given Ana has the trimmer on loan, when scheduled, then refused tool-checked-out`() {
        refusedWith(
            shed() + ToolCheckedOut(l1, trimmer, ana, 7, LocalDate.of(2026, 10, 20), earlier),
            schedule(trimmer, "Sharpen"),
            "INV-STM-5",
            "tool-checked-out",
            "That tool is on loan.",
        )
    }

    @Test
    fun `INV-STM-5 given Ana's loan of the trimmer ended with Tool Returned, when scheduled, then Scheduled is recorded`() {
        recordedAs(
            shed() +
                ToolCheckedOut(l1, trimmer, ana, 7, LocalDate.of(2026, 10, 20), earlier) +
                ToolReturned(l1, trimmer, earlier, LocalDate.of(2026, 10, 18), false),
            schedule(trimmer, "Sharpen"),
            ToolMaintenanceScheduled(trimmer, "Hedge trimmer", listOf("Sharpen"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-6 given the drill is set aside for Ben, when scheduled, then refused tool-set-aside`() {
        refusedWith(
            shed() + HoldPlaced(h1, drill, ben, earlier) + HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 11, 25), earlier),
            schedule(drill, "Clean chuck"),
            "INV-STM-6",
            "tool-set-aside",
            "That tool is set aside for a neighbor.",
        )
    }

    @Test
    fun `INV-STM-6 given Ben's fulfilled hold on the drill expired, when scheduled, then Scheduled is recorded`() {
        recordedAs(
            shed() +
                HoldPlaced(h1, drill, ben, earlier) +
                HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 11, 25), earlier) +
                HoldExpired(h1, earlier),
            schedule(drill, "Clean chuck"),
            ToolMaintenanceScheduled(drill, "Cordless drill", listOf("Clean chuck"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-7 given Cara's hold on the drill is waiting, when scheduled, then refused tool-has-holds`() {
        refusedWith(
            shed() + HoldPlaced(h2, drill, cara, earlier),
            schedule(drill, "Clean chuck"),
            "INV-STM-7",
            "tool-has-holds",
            "That tool has holds waiting.",
        )
    }

    @Test
    fun `INV-STM-7 given the drill was just returned and a hold is not yet fulfilled, when scheduled, then refused tool-has-holds`() {
        refusedWith(
            shed() +
                ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 20), earlier) +
                HoldPlaced(h2, drill, cara, earlier) +
                ToolReturned(l1, drill, earlier, LocalDate.of(2026, 10, 18), false),
            schedule(drill, "Clean chuck"),
            "INV-STM-7",
            "tool-has-holds",
            "That tool has holds waiting.",
        )
    }

    @Test
    fun `INV-STM-7 given Cara's hold on the drill was cancelled, when scheduled, then Scheduled is recorded`() {
        recordedAs(
            shed() + HoldPlaced(h2, drill, cara, earlier) + HoldCancelled(h2, earlier),
            schedule(drill, "Clean chuck"),
            ToolMaintenanceScheduled(drill, "Cordless drill", listOf("Clean chuck"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-8 INV-STM-10 given the ladder is in maintenance, when Dana adds a task, then Tasks Changed is recorded`() {
        recordedAs(
            shed() + scheduled(ladder, "Ladder", listOf("Oil hinges")),
            schedule(ladder, "Oil hinges", "Check rungs"),
            ToolMaintenanceTasksChanged(ladder, listOf("Oil hinges", "Check rungs"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-8 given the ladder has three tasks, when one is removed and another reworded, then exactly those two are recorded`() {
        recordedAs(
            shed() + scheduled(ladder, "Ladder", listOf("Oil hinges", "Check rungs", "Paint")),
            schedule(ladder, "Oil hinges", "Check all rungs"),
            ToolMaintenanceTasksChanged(ladder, listOf("Oil hinges", "Check all rungs"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-8 INV-STM-11 given the ladder has notes, when sent with no notes, then the notes are cleared`() {
        recordedAs(
            shed() + scheduled(ladder, "Ladder", listOf("Oil hinges"), notes = "Squeaks"),
            schedule(ladder, "Oil hinges"),
            ToolMaintenanceTasksChanged(ladder, listOf("Oil hinges"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-12 given the ladder is in maintenance, when the same tasks and notes are sent again, then nothing is recorded`() {
        fixture
            .given()
            .recorded(shed() + scheduled(ladder, "Ladder", listOf("Oil hinges"), notes = "Squeaks"))
            .`when`()
            .command(schedule(ladder, " Oil hinges ", notes = "Squeaks"))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-STM-8 given the ladder is in maintenance, when the same tasks come in a different order, then Tasks Changed is recorded`() {
        recordedAs(
            shed() + scheduled(ladder, "Ladder", listOf("Oil hinges", "Check rungs")),
            schedule(ladder, "Check rungs", "Oil hinges"),
            ToolMaintenanceTasksChanged(ladder, listOf("Check rungs", "Oil hinges"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-8 given an edit already changed the work, when the changed work is sent again, then nothing is recorded`() {
        fixture
            .given()
            .recorded(
                shed() +
                    scheduled(ladder, "Ladder", listOf("Oil hinges")) +
                    ToolMaintenanceTasksChanged(ladder, listOf("Check rungs"), null, "Dana", earlier),
            ).`when`()
            .command(schedule(ladder, "Check rungs"))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-STM-2 INV-STM-8 given the ladder is in maintenance, when an edit leaves no task, then refused tasks-missing`() {
        refusedWith(
            shed() + scheduled(ladder, "Ladder", listOf("Oil hinges")),
            schedule(ladder, "  ", ""),
            "INV-STM-2",
            "tasks-missing",
            "Say what work the tool needs.",
        )
    }

    @Test
    fun `INV-STM-4 INV-STM-8 given the trimmer was in maintenance and retired, when edited, then refused tool-retired`() {
        refusedWith(
            shed() + scheduled(trimmer, "Hedge trimmer", listOf("Sharpen")) + ToolRetired(trimmer, earlier),
            schedule(trimmer, "Sharpen"),
            "INV-STM-4",
            "tool-retired",
            "That tool has been retired.",
        )
    }

    @Test
    fun `INV-STM-8 INV-STM-9 given completed after an edit, when scheduled, then Scheduled not Tasks Changed is recorded`() {
        recordedAs(
            shed() +
                scheduled(ladder, "Ladder", listOf("Oil hinges")) +
                ToolMaintenanceTasksChanged(ladder, listOf("Oil hinges", "Paint"), null, "Dana", earlier) +
                ToolMaintenanceCompleted(ladder, earlier),
            schedule(ladder, "Check rungs"),
            ToolMaintenanceScheduled(ladder, "Ladder", listOf("Check rungs"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-9 given maintenance was completed, when scheduled again with the same work, then Scheduled is recorded`() {
        recordedAs(
            shed() + scheduled(ladder, "Ladder", listOf("Check rungs")) + ToolMaintenanceCompleted(ladder, earlier),
            schedule(ladder, "Check rungs"),
            ToolMaintenanceScheduled(ladder, "Ladder", listOf("Check rungs"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-8 given the ladder is in maintenance and a hold is waiting, when edited, then loan and hold checks are not applied`() {
        recordedAs(
            shed() + scheduled(ladder, "Ladder", listOf("Oil hinges")) + HoldPlaced(h1, ladder, ben, earlier),
            schedule(ladder, "Paint"),
            ToolMaintenanceTasksChanged(ladder, listOf("Paint"), null, "Dana", now),
        )
    }

    @Test
    fun `INV-STM-1 given the ladder is free, when a caller without the shed-manager role asks, then refused not-shed-manager`() {
        val refused = assertThrows<CommandRejected> { Caller(neighborId = ana, isShedManager = false).requireMaintenanceScheduler(null) }
        assertThat(refused.reason).isEqualTo("not-shed-manager")
        assertThat(refused.invariant).isEqualTo("INV-STM-1")
        assertThat(refused.message).isEqualTo("Only the shed manager can schedule tool maintenance.")
    }

    @Test
    fun `INV-STM-1 given nobody is signed in, when they ask to schedule, then refused not-signed-in`() {
        val refused = assertThrows<CommandRejected> { Caller.ANONYMOUS.requireMaintenanceScheduler(null) }
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.invariant).isEqualTo("INV-STM-1")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }

    @Test
    fun `INV-STM-10 given the shed-manager role with no name, when they ask to schedule, then refused not-signed-in`() {
        val refused =
            assertThrows<CommandRejected> { Caller(neighborId = null, isShedManager = true).requireMaintenanceScheduler(" ") }
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }
}
