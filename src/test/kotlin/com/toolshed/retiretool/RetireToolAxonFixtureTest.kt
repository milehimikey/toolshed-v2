package com.toolshed.retiretool

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
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

/** One test per scenario in slices/retire-tool.md, built from the slice's own configure function. */
class RetireToolAxonFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
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
        fixture = AxonTestFixture.with(RetireToolConfiguration.configure(EventSourcingConfigurer.create(), clock))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    private fun shed(): List<Any> =
        listOf(
            ToolAdded(ladder, "Ladder", null, 7, now),
            ToolAdded(drill, "Cordless drill", null, 7, now),
            ToolAdded(trimmer, "Hedge trimmer", null, 7, now),
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            NeighborRegistered(ben, "Ben Cole", "ben@example.org", now),
            NeighborRegistered(cara, "Cara Lind", "cara@example.org", now),
        )

    private fun assertRejected(
        ex: Throwable,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        val rejected =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<RetireToolRejected>()
                .firstOrNull()
        requireNotNull(rejected) { "no RetireToolRejected in the exception chain: $ex" }
        assertThat(rejected.invariant).isEqualTo(invariant)
        assertThat(rejected.reason).isEqualTo(reason)
        assertThat(rejected.message).isEqualTo(sentence)
    }

    @Test
    fun `INV-RTR-6 given the ladder is in the shed with no holds, when the shed manager retires it, then recorded`() {
        fixture
            .given()
            .recorded(shed())
            .`when`()
            .command(RetireTool(ladder))
            .then()
            .success()
            .events(ToolRetired(ladder, now))
    }

    @Test
    fun `INV-RTR-6 given the drill is set aside for Ben with Cara's hold waiting, when the shed manager retires it, then recorded`() {
        fixture
            .given()
            .recorded(
                shed() +
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now) +
                    HoldPlaced(h1, drill, ben, now) +
                    HoldPlaced(h2, drill, cara, now) +
                    ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 10), false) +
                    HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 13), now),
            ).`when`()
            .command(RetireTool(drill))
            .then()
            .success()
            .events(ToolRetired(drill, now))
    }

    @Test
    fun `INV-RTR-6 given the trimmer is on loan to Ana with Ben's hold waiting, when retired, then recorded and the loan is unchanged`() {
        fixture
            .given()
            .recorded(
                shed() +
                    ToolCheckedOut(l1, trimmer, ana, 7, LocalDate.of(2026, 10, 20), now) +
                    HoldPlaced(h1, trimmer, ben, now),
            ).`when`()
            .command(RetireTool(trimmer))
            .then()
            .success()
            .events(ToolRetired(trimmer, now))
    }

    @Test
    fun `INV-RTR-6 given the drill has just been returned with Ben's hold waiting, when the shed manager retires it, then recorded`() {
        fixture
            .given()
            .recorded(
                shed() +
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now) +
                    HoldPlaced(h1, drill, ben, now) +
                    ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 10), false),
            ).`when`()
            .command(RetireTool(drill))
            .then()
            .success()
            .events(ToolRetired(drill, now))
    }

    @Test
    fun `INV-RTR-2 given Tool Retired was recorded for the ladder, when the shed manager retires it again, then nothing new is recorded`() {
        fixture
            .given()
            .recorded(shed() + ToolRetired(ladder, now))
            .`when`()
            .command(RetireTool(ladder))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-RTR-2 given Tool Lost retired the trimmer, when the shed manager retires it, then nothing new is recorded`() {
        fixture
            .given()
            .recorded(shed() + ToolCheckedOut(l1, trimmer, ana, 7, LocalDate.of(2026, 10, 12), now) + ToolLost(l1, trimmer, now))
            .`when`()
            .command(RetireTool(trimmer))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-RTR-10 given no tool T9 has been added, when the shed manager retires T9, then refused tool-not-found`() {
        fixture
            .given()
            .recorded(shed())
            .`when`()
            .command(RetireTool(t9))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-RTR-10", "tool-not-found", "There is no such tool.") }
    }

    @Test
    fun `INV-RTR-1 given the ladder is in the shed, when a caller without the shed-manager role asks, then refused not-shed-manager`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller(neighborId = ana, isShedManager = false)) { requireRetirer() }
            }
        assertThat(refused.reason).isEqualTo("not-shed-manager")
        assertThat(refused.invariant).isEqualTo("INV-RTR-1")
        assertThat(refused.message).isEqualTo("Only the shed manager can retire tools.")
    }

    @Test
    fun `INV-RTR-1 given nobody is signed in, when they ask to retire the ladder, then refused not-signed-in`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller.ANONYMOUS) { requireRetirer() }
            }
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.invariant).isEqualTo("INV-RTR-1")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }
}
