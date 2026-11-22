package com.toolshed.completetoolmaintenance

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.LoanId
import com.toolshed.axon.ToolId
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceScheduled
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceTasksChanged
import com.toolshed.web.Caller
import com.toolshed.web.CallerArgumentResolver
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
import java.time.ZoneOffset
import java.util.UUID

/** One test per scenario in slices/complete-tool-maintenance.md, built from the slice's own configure function. */
class CompleteToolMaintenanceAxonFixtureTest {
    private val now = Instant.parse("2026-11-20T09:00:00Z")
    private val trimmer = ToolId(UUID.nameUUIDFromBytes("trimmer".toByteArray()))
    private val ladder = ToolId(UUID.nameUUIDFromBytes("ladder".toByteArray()))
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val t9 = ToolId(UUID.nameUUIDFromBytes("t9".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        fixture = AxonTestFixture.with(CompleteToolMaintenanceConfiguration.configure(EventSourcingConfigurer.create(), clock))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    private fun shed(): List<Any> =
        listOf(
            ToolAdded(trimmer, "Hedge trimmer", null, 7, now),
            ToolAdded(ladder, "Ladder", null, 7, now),
            ToolAdded(drill, "Cordless drill", null, 7, now),
        )

    private fun scheduled(
        tool: ToolId,
        vararg tasks: String,
    ) = ToolMaintenanceScheduled(tool, "Hedge trimmer", tasks.toList(), null, "Mike", now)

    private fun assertRejected(
        ex: Throwable,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        val rejected =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<CompleteToolMaintenanceRejected>()
                .firstOrNull()
        requireNotNull(rejected) { "no CompleteToolMaintenanceRejected in the exception chain: $ex" }
        assertThat(rejected.invariant).isEqualTo(invariant)
        assertThat(rejected.reason).isEqualTo(reason)
        assertThat(rejected.message).isEqualTo(sentence)
    }

    @Test
    fun `INV-CTMN-5 given the trimmer was sent for maintenance, when completed, then only the completion is recorded`() {
        fixture
            .given()
            .recorded(shed() + scheduled(trimmer, "sharpen", "clean"))
            .`when`()
            .command(CompleteToolMaintenance(trimmer))
            .then()
            .success()
            .events(ToolMaintenanceCompleted(trimmer, now))
    }

    @Test
    fun `INV-CTMN-2 given the trimmer's maintenance was completed, when completed again, then it succeeds and records nothing`() {
        fixture
            .given()
            .recorded(shed() + scheduled(trimmer, "sharpen") + ToolMaintenanceCompleted(trimmer, now))
            .`when`()
            .command(CompleteToolMaintenance(trimmer))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-CTMN-2 given the ladder was never sent for maintenance, when completed, then it succeeds with nothing recorded`() {
        fixture
            .given()
            .recorded(shed())
            .`when`()
            .command(CompleteToolMaintenance(ladder))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-CTMN-5 given the tasks were changed, when the shed manager completes it, then only the completion is recorded`() {
        fixture
            .given()
            .recorded(
                shed() + scheduled(trimmer, "sharpen") +
                    ToolMaintenanceTasksChanged(trimmer, listOf("sharpen", "replace the guard"), null, "Mike", now),
            ).`when`()
            .command(CompleteToolMaintenance(trimmer))
            .then()
            .success()
            .events(ToolMaintenanceCompleted(trimmer, now))
    }

    @Test
    fun `INV-CTMN-5 given the trimmer was scheduled again after completing, when completed, then the completion is recorded`() {
        fixture
            .given()
            .recorded(
                shed() + scheduled(trimmer, "sharpen") + ToolMaintenanceCompleted(trimmer, now) + scheduled(trimmer, "oil the blade"),
            ).`when`()
            .command(CompleteToolMaintenance(trimmer))
            .then()
            .success()
            .events(ToolMaintenanceCompleted(trimmer, now))
    }

    @Test
    fun `INV-CTMN-4 given the trimmer is in maintenance and then retired, when completed, then refused tool-retired`() {
        fixture
            .given()
            .recorded(shed() + scheduled(trimmer, "sharpen") + ToolRetired(trimmer, now))
            .`when`()
            .command(CompleteToolMaintenance(trimmer))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CTMN-4", "tool-retired", "That tool has been retired.") }
            .noEvents()
    }

    @Test
    fun `INV-CTMN-4 given Tool Lost is recorded for the drill, when its maintenance is completed, then refused tool-retired`() {
        fixture
            .given()
            .recorded(shed() + ToolLost(l1, drill, now))
            .`when`()
            .command(CompleteToolMaintenance(drill))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CTMN-4", "tool-retired", "That tool has been retired.") }
            .noEvents()
    }

    @Test
    fun `INV-CTMN-3 given no tool T9 has been added, when the shed manager completes T9's maintenance, then refused tool-not-found`() {
        fixture
            .given()
            .recorded(shed())
            .`when`()
            .command(CompleteToolMaintenance(t9))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CTMN-3", "tool-not-found", "There is no such tool.") }
            .noEvents()
    }

    @Test
    fun `INV-CTMN-1 given the trimmer is in maintenance, when a non-manager asks, then refused not-shed-manager`() {
        val refused =
            assertThrows<CommandRejected> {
                with(CallerArgumentResolver.resolveCaller(UUID.randomUUID().toString(), null)) { requireMaintenanceCompleter() }
            }
        assertThat(refused.reason).isEqualTo("not-shed-manager")
        assertThat(refused.invariant).isEqualTo("INV-CTMN-1")
        assertThat(refused.message).isEqualTo("Only the shed manager can complete tool maintenance.")
    }

    @Test
    fun `INV-CTMN-1 given the trimmer is in maintenance, when a caller with no sign-in asks, then refused not-signed-in`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller.ANONYMOUS) { requireMaintenanceCompleter() }
            }
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.invariant).isEqualTo("INV-CTMN-1")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }
}
