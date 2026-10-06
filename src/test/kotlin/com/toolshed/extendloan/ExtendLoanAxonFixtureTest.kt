package com.toolshed.extendloan

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
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
 * One test per scenario in slices/extend-loan.md. Unless a scenario says otherwise: Ana has the
 * cordless drill (7-day default) on loan L1, due 12 October, never extended, no hold waiting, and
 * today is 10 October.
 */
class ExtendLoanAxonFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val now = Instant.parse("2026-10-06T04:00:00Z")

    /** The instant the fixture's clock gives the handler, so expected Loan Extended events match it. */
    private var recordedAt: Instant = now
    private val oct10 = LocalDate.of(2026, 10, 10)
    private val oct12 = LocalDate.of(2026, 10, 12)
    private val oct13 = LocalDate.of(2026, 10, 13)

    private val drill = ToolId(uuid("d1"))
    private val ladder = ToolId(uuid("d2"))
    private val ana = NeighborId(uuid("a1"))
    private val ben = NeighborId(uuid("b1"))
    private val cara = NeighborId(uuid("c1"))
    private val l1 = LoanId(uuid("11"))
    private val l2 = LoanId(uuid("12"))
    private val l9 = LoanId(uuid("19"))
    private val benHold = HoldId(uuid("h1"))
    private val caraHold = HoldId(uuid("h2"))
    private val anaHold = HoldId(uuid("h3"))

    private var fixture: AxonTestFixture? = null

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    /** A fixture whose "today" is [today] in the shed's zone. */
    private fun on(today: LocalDate): AxonTestFixture {
        val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)
        recordedAt = clock.instant()
        return AxonTestFixture.with(ExtendLoanConfiguration.configure(EventSourcingConfigurer.create(), clock)).also { fixture = it }
    }

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    private fun drillOnLoan(
        dueDate: LocalDate = oct12,
        periodDays: Int = 7,
    ): List<Any> =
        listOf(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            ToolCheckedOut(l1, drill, ana, periodDays, dueDate, now),
        )

    private fun extend(
        by: NeighborId = ana,
        loan: LoanId = l1,
        from: LocalDate = oct12,
    ) = ExtendLoan(by, loan, from)

    private fun extended(
        from: LocalDate,
        to: LocalDate,
        loan: LoanId = l1,
    ) = LoanExtended(loan, from, to, recordedAt)

    // --- happy path and the period rules ---

    @Test
    fun `INV-EXT-3 given L1 as above, when Ana extends from 12 October, then due 19 October`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(extend())
            .then()
            .success()
            .events(extended(oct12, LocalDate.of(2026, 10, 19)))
    }

    @Test
    fun `INV-EXT-5 given today is 12 October, when Ana extends on the due date, then recorded`() {
        on(oct12)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(extend())
            .then()
            .success()
            .events(extended(oct12, LocalDate.of(2026, 10, 19)))
    }

    @Test
    fun `INV-EXT-5 given today is 13 October, when Ana extends from 12 October, then refused loan-overdue`() {
        on(oct13)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(extend())
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-EXT-5", "loan-overdue", "This loan is overdue and can't be extended. Please return the tool.")
            }
    }

    @Test
    fun `INV-EXT-6 given L1 has been extended once and is due 19 October, when Ana extends from 19 October, then due 26 October`() {
        on(LocalDate.of(2026, 10, 18))
            .given()
            .recorded(drillOnLoan() + extended(oct12, LocalDate.of(2026, 10, 19)))
            .`when`()
            .command(extend(from = LocalDate.of(2026, 10, 19)))
            .then()
            .success()
            .events(extended(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun `INV-EXT-3 given Ana checked the drill out for 3 days, when she extends from 8 October, then 7 days not 3`() {
        val due = LocalDate.of(2026, 10, 8)
        on(LocalDate.of(2026, 10, 7))
            .given()
            .recorded(drillOnLoan(dueDate = due, periodDays = 3))
            .`when`()
            .command(extend(from = due))
            .then()
            .success()
            .events(extended(due, LocalDate.of(2026, 10, 15)))
    }

    // --- holds ---

    @Test
    fun `INV-EXT-4 given Ben has a hold waiting on the drill, when Ana extends, then refused hold-waiting`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan() + HoldPlaced(benHold, drill, ben, now))
            .`when`()
            .command(extend())
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-EXT-4", "hold-waiting", "Someone is waiting for this tool, so this loan can't be extended.")
            }
    }

    @Test
    fun `INV-EXT-4 given Cara's hold was behind Ana's and is still waiting, when Ana extends, then refused hold-waiting`() {
        on(oct10)
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    HoldPlaced(anaHold, drill, ana, now),
                    HoldPlaced(caraHold, drill, cara, now),
                    ToolCheckedOut(l1, drill, ana, 7, oct12, now),
                ),
            ).`when`()
            .command(extend())
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-EXT-4", "hold-waiting", "Someone is waiting for this tool, so this loan can't be extended.")
            }
    }

    @Test
    fun `INV-EXT-4 given Ben placed a hold and then cancelled it, when Ana extends, then recorded`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan() + HoldPlaced(benHold, drill, ben, now) + HoldCancelled(benHold, now))
            .`when`()
            .command(extend())
            .then()
            .success()
            .events(extended(oct12, LocalDate.of(2026, 10, 19)))
    }

    // --- other loans and suspension ---

    @Test
    fun `INV-EXT-5 given Ana's ladder loan is overdue, when she extends L1, then L1 is recorded`() {
        on(oct10)
            .given()
            .recorded(
                drillOnLoan() +
                    ToolAdded(ladder, "Ladder", null, 7, now) +
                    ToolCheckedOut(l2, ladder, ana, 7, LocalDate.of(2026, 10, 4), now),
            ).`when`()
            .command(extend())
            .then()
            .success()
            .events(extended(oct12, LocalDate.of(2026, 10, 19)))
    }

    @Test
    fun `INV-EXT-7 given Ana is suspended, when she extends L1, then refused neighbor-suspended`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan() + NeighborSuspended(ana, now))
            .`when`()
            .command(extend())
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-EXT-7", "neighbor-suspended", "You can't extend loans while your account is suspended.")
            }
    }

    @Test
    fun `INV-EXT-7 given Ana was suspended and reinstated, when she extends L1, then recorded`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan() + NeighborSuspended(ana, now) + NeighborReinstated(ana, now))
            .`when`()
            .command(extend())
            .then()
            .success()
            .events(extended(oct12, LocalDate.of(2026, 10, 19)))
    }

    // --- retired, returned, lost ---

    @Test
    fun `INV-EXT-8 given the drill was retired while on loan, when Ana extends, then refused tool-retired`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan() + ToolRetired(drill, now))
            .`when`()
            .command(extend())
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(
                    ex,
                    "INV-EXT-8",
                    "tool-retired",
                    "This tool has been retired, so this loan can't be extended. Please return it by the due date.",
                )
            }
    }

    @Test
    fun `INV-EXT-2 given Ana returned the drill, when a request to extend L1 arrives, then refused loan-ended`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan() + ToolReturned(l1, drill, now, oct10, false))
            .`when`()
            .command(extend())
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-EXT-2", "loan-ended", "This loan has ended.") }
    }

    @Test
    fun `INV-EXT-2 given Tool Lost was recorded for L1, when a request to extend L1 arrives, then refused loan-ended`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan() + ToolLost(l1, drill, now))
            .`when`()
            .command(extend())
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-EXT-2", "loan-ended", "This loan has ended.") }
    }

    // --- identity and loan existence ---

    @Test
    fun `INV-EXT-9 given no loan L9 was checked out, when a request to extend L9 arrives, then refused loan-not-found`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(extend(loan = l9))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-EXT-9", "loan-not-found", "There is no such loan.") }
    }

    @Test
    fun `INV-EXT-1 given L1 is Ana's loan, when Ben, naming himself, extends it, then refused not-your-loan`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(extend(by = ben))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-EXT-1", "not-your-loan", "You can only extend your own loans.") }
    }

    @Test
    fun `INV-EXT-1 given a caller with no sign-in, when they ask to extend L1, then refused not-signed-in`() {
        assertThrows<CommandRejected> { requireBorrowerFor(com.toolshed.web.Caller.ANONYMOUS, extend()) }
            .also { assertThat(it.reason).isEqualTo("not-signed-in") }
            .also { assertThat(it.invariant).isEqualTo("INV-EXT-1") }
            .also { assertThat(it.message).isEqualTo("Please sign in.") }
    }

    @Test
    fun `INV-EXT-1 given L1 is Ana's loan, when a request naming Ana comes from Ben, then refused requester-mismatch`() {
        assertThrows<CommandRejected> {
            requireBorrowerFor(com.toolshed.web.Caller(neighborId = ben, isShedManager = false), extend(by = ana))
        }.also { assertThat(it.reason).isEqualTo("requester-mismatch") }
            .also { assertThat(it.invariant).isEqualTo("INV-EXT-1") }
            .also { assertThat(it.message).isEqualTo("You can only extend loans for yourself.") }
    }

    @Test
    fun `INV-EXT-1 given L1 is Ana's loan, when Ana signed in extends it, then the identity check passes`() {
        requireBorrowerFor(com.toolshed.web.Caller(neighborId = ana, isShedManager = false), extend())
    }

    private fun requireBorrowerFor(
        caller: com.toolshed.web.Caller,
        command: ExtendLoan,
    ) {
        with(caller) { requireBorrower(command) }
    }

    // --- repeats and stale due dates ---

    @Test
    fun `INV-EXT-10 given Loan Extended was recorded from 12 October, when the same request arrives again, then records nothing`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan() + extended(oct12, LocalDate.of(2026, 10, 19)))
            .`when`()
            .command(extend())
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-EXT-10 given the extension was recorded, then Ben placed a hold, when the same request arrives, then records nothing`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan() + extended(oct12, LocalDate.of(2026, 10, 19)) + HoldPlaced(benHold, drill, ben, now))
            .`when`()
            .command(extend())
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-EXT-10 given L1 is due 12 October and never extended, when a request from 5 October arrives, then refused due-date-changed`() {
        on(oct10)
            .given()
            .recorded(drillOnLoan())
            .`when`()
            .command(extend(from = LocalDate.of(2026, 10, 5)))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-EXT-10", "due-date-changed", "This loan's due date has changed. Check it and try again.")
            }
    }

    private fun assertRejected(
        ex: Throwable,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        val rejected =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<ExtendLoanRejected>()
                .firstOrNull()
        requireNotNull(rejected) { "no ExtendLoanRejected in the exception chain: $ex" }
        assertThat(rejected.invariant).isEqualTo(invariant)
        assertThat(rejected.reason).isEqualTo(reason)
        assertThat(rejected.message).isEqualTo(sentence)
    }
}
