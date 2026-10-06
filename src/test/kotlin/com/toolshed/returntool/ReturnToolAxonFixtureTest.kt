package com.toolshed.returntool

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.suspendneighbor.NeighborSuspended
import com.toolshed.web.Caller
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.AxonTestPhase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * One test per scenario in slices/return-tool.md. The clock is set to the shed time the scenario names
 * (America/Denver, UTC-6 in October), so the calendar date and the lateness are the doc's.
 */
class ReturnToolAxonFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val oct12 = LocalDate.of(2026, 10, 12)
    private val t1 = ToolId(UUID.nameUUIDFromBytes("t1".toByteArray()))
    private val t2 = ToolId(UUID.nameUUIDFromBytes("t2".toByteArray()))
    private val ana = NeighborId(UUID.nameUUIDFromBytes("ana".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("ben".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val l2 = LoanId(UUID.nameUUIDFromBytes("l2".toByteArray()))
    private val l3 = LoanId(UUID.nameUUIDFromBytes("l3".toByteArray()))
    private val l9 = LoanId(UUID.nameUUIDFromBytes("l9".toByteArray()))
    private val setup = Instant.parse("2026-10-01T16:00:00Z")

    private var fixture: AxonTestFixture? = null

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    /** A fixture whose clock reads [at]. */
    private fun at(at: Instant): AxonTestFixture {
        val clock = Clock.fixed(at, zone)
        return AxonTestFixture.with(ReturnToolConfiguration.configure(EventSourcingConfigurer.create(), clock)).also { fixture = it }
    }

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    /** The cordless drill (7-day default) is on loan to Ana as L1, due 12 October. */
    private fun drillOnLoan(): List<Any> =
        listOf(
            ToolAdded(t1, "Cordless drill", null, 7, setup),
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", setup),
            NeighborRegistered(ben, "Ben Cole", "ben@example.org", setup),
            ToolCheckedOut(l1, t1, ana, 7, oct12, setup),
        )

    private fun assertRejected(
        ex: Throwable,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        val rejected =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<ReturnToolRejected>()
                .firstOrNull()
        requireNotNull(rejected) { "no ReturnToolRejected in the exception chain: $ex" }
        assertThat(rejected.invariant).isEqualTo(invariant)
        assertThat(rejected.reason).isEqualTo(reason)
        assertThat(rejected.message).isEqualTo(sentence)
    }

    // --- on time, late, and extended ---

    @Test
    fun `INV-RET-1 INV-RET-3 INV-RET-4 given L1 with no holds, when Ana returns it at 15 00 on 11 October, then on time and recorded`() {
        val now = Instant.parse("2026-10-11T21:00:00Z")
        at(now)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(ReturnTool(ana, l1))
            .then()
            .success()
            .events(ToolReturned(l1, t1, now, LocalDate.of(2026, 10, 11), false))
    }

    @Test
    fun `INV-RET-4 given L1 is due 12 October, when Ana returns it at 23 59 on 12 October, then on time`() {
        val now = Instant.parse("2026-10-13T05:59:00Z")
        at(now)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(ReturnTool(ana, l1))
            .then()
            .success()
            .events(ToolReturned(l1, t1, now, oct12, false))
    }

    @Test
    fun `INV-RET-4 INV-RET-5 given L1 was due 12 October and the notice was sent, when Ana returns it at 00 05 on 13 October, then late`() {
        val now = Instant.parse("2026-10-13T06:05:00Z")
        at(now)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(ReturnTool(ana, l1))
            .then()
            .success()
            .events(ToolReturned(l1, t1, now, LocalDate.of(2026, 10, 13), true))
    }

    @Test
    fun `INV-RET-4 given L1 was due 12 October, when Ana returns it at 00 00 30 on 13 October, then date and lateness agree`() {
        // 06:00:30 UTC is 00:00:30 in Denver: the shed day is already the 13th, so the return is late.
        val now = Instant.parse("2026-10-13T06:00:30Z")
        at(now)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(ReturnTool(ana, l1))
            .then()
            .success()
            .events(ToolReturned(l1, t1, now, LocalDate.of(2026, 10, 13), true))
    }

    @Test
    fun `INV-RET-5 given L1 is overdue, when Ana returns it on 14 October, then recorded as late`() {
        val now = Instant.parse("2026-10-14T16:00:00Z")
        at(now)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(ReturnTool(ana, l1))
            .then()
            .success()
            .events(ToolReturned(l1, t1, now, LocalDate.of(2026, 10, 14), true))
    }

    @Test
    fun `INV-RET-4 given L1 was extended to 19 October, when Ana returns it on 15 October, then on time against 19 October`() {
        val now = Instant.parse("2026-10-15T16:00:00Z")
        at(now)
            .given()
            .recorded(drillOnLoan() + LoanExtended(l1, oct12, LocalDate.of(2026, 10, 19), setup))
            .`when`()
            .command(ReturnTool(ana, l1))
            .then()
            .success()
            .events(ToolReturned(l1, t1, now, LocalDate.of(2026, 10, 15), false))
    }

    // --- holds, suspension, retirement ---

    @Test
    fun `INV-RET-3 given Ben's hold is at the front of the queue, when Ana returns L1, then only Tool Returned is recorded`() {
        val now = Instant.parse("2026-10-11T21:00:00Z")
        at(now)
            .given()
            .recorded(drillOnLoan() + com.toolshed.placehold.HoldPlaced(com.toolshed.axon.HoldId(UUID.randomUUID()), t1, ben, setup))
            .`when`()
            .command(ReturnTool(ana, l1))
            .then()
            .success()
            .events(ToolReturned(l1, t1, now, LocalDate.of(2026, 10, 11), false))
    }

    @Test
    fun `INV-RET-7 given Ana is suspended and has L1 out, when she returns it, then recorded`() {
        val now = Instant.parse("2026-10-11T21:00:00Z")
        at(now)
            .given()
            .recorded(drillOnLoan() + NeighborSuspended(ana, setup))
            .`when`()
            .command(ReturnTool(ana, l1))
            .then()
            .success()
            .events(ToolReturned(l1, t1, now, LocalDate.of(2026, 10, 11), false))
    }

    @Test
    fun `INV-RET-9 given the ladder was retired while Ana had it out, when she returns L2, then recorded and it stays retired`() {
        val now = Instant.parse("2026-10-11T21:00:00Z")
        at(now)
            .given()
            .recorded(
                listOf(
                    ToolAdded(t2, "Ladder", null, 7, setup),
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", setup),
                    ToolCheckedOut(l2, t2, ana, 7, oct12, setup),
                    ToolRetired(t2, setup),
                ),
            ).`when`()
            .command(ReturnTool(ana, l2))
            .then()
            .success()
            .events(ToolReturned(l2, t2, now, LocalDate.of(2026, 10, 11), false))
    }

    // --- repeats and ended loans ---

    @Test
    fun `INV-RET-10 given Tool Returned was recorded for L1, when Ana's same return arrives again, then nothing new is recorded`() {
        val now = Instant.parse("2026-10-11T21:00:00Z")
        at(now)
            .given()
            .recorded(drillOnLoan() + ToolReturned(l1, t1, setup, LocalDate.of(2026, 10, 10), false))
            .`when`()
            .command(ReturnTool(ana, l1))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-RET-2 given Tool Lost was recorded for L3, when Ana asks to return it, then refused loan-ended`() {
        val now = Instant.parse("2026-10-11T21:00:00Z")
        at(now)
            .given()
            .recorded(
                listOf(
                    ToolAdded(t2, "Ladder", null, 7, setup),
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", setup),
                    ToolCheckedOut(l3, t2, ana, 7, oct12, setup),
                    ToolLost(l3, t2, setup),
                ),
            ).`when`()
            .command(ReturnTool(ana, l3))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-RET-2", "loan-ended", "This loan has ended.") }
    }

    // --- identity and existence ---

    @Test
    fun `INV-RET-1 given Ana has L1 out, when Ben, for himself, asks to return it, then refused not-your-loan`() {
        val now = Instant.parse("2026-10-11T21:00:00Z")
        at(now)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(ReturnTool(ben, l1))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-RET-1", "not-your-loan", "You can only return your own loans.") }
    }

    @Test
    fun `INV-RET-1 given Ana has L1 out, when a request from Ben asks to return it for Ana, then refused requester-mismatch`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller(neighborId = ben, isShedManager = false)) { requireReturner(ReturnTool(ana, l1)) }
            }
        assertThat(refused.reason).isEqualTo("requester-mismatch")
        assertThat(refused.invariant).isEqualTo("INV-RET-1")
        assertThat(refused.message).isEqualTo("You can only return loans for yourself.")
    }

    @Test
    fun `INV-RET-11 given no loan has ID L9, when Ana asks to return it, then refused loan-not-found`() {
        val now = Instant.parse("2026-10-11T21:00:00Z")
        at(now)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(ReturnTool(ana, l9))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-RET-11", "loan-not-found", "There is no such loan.") }
    }

    @Test
    fun `INV-RET-12 given nobody is signed in, when a request for returning a tool arrives, then refused not-signed-in`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller.ANONYMOUS) { requireReturner(ReturnTool(ana, l1)) }
            }
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.invariant).isEqualTo("INV-RET-12")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }
}
