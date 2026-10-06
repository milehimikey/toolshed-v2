package com.toolshed.checkouttool

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.extendloan.LoanExtended
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.AxonTestPhase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scenario tests for Check Out Tool, run through [AxonTestFixture] built from the slice's own
 * configure function. Unless a test says otherwise, today is 5 October 2026 in the shed's zone.
 */
class CheckOutToolAxonFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val today = LocalDate.of(2026, 10, 5)
    private val now: Instant = today.atTime(9, 0).atZone(zone).toInstant()

    private var fixture: AxonTestFixture? = null

    private val drill = ToolId(UUID.randomUUID())
    private val ladder = ToolId(UUID.randomUUID())
    private val ana = NeighborId(UUID.randomUUID())
    private val ben = NeighborId(UUID.randomUUID())
    private val cara = NeighborId(UUID.randomUUID())
    private val hold1 = HoldId(UUID.randomUUID())
    private val hold2 = HoldId(UUID.randomUUID())

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    /** A fresh fixture built from the slice's configure function, on [on], with the drill and three neighbors. */
    private fun world(on: LocalDate = today): AxonTestPhase.Given {
        fixture?.stop()
        val at = on.atTime(9, 0).atZone(zone).toInstant()
        val configurer = CheckOutToolConfiguration.configure(EventSourcingConfigurer.create(), Clock.fixed(at, zone))
        val built = AxonTestFixture.with(configurer)
        fixture = built
        return built
            .given()
            .event(ToolAdded(drill, "Cordless drill", null, 7, now))
            .event(ToolAdded(ladder, "Ladder", null, 7, now))
            .event(NeighborRegistered(ana, "Ana", "ana@example.com", now))
            .event(NeighborRegistered(ben, "Ben", "ben@example.com", now))
            .event(NeighborRegistered(cara, "Cara", "cara@example.com", now))
    }

    private fun checkout(
        loan: LoanId,
        tool: ToolId,
        neighbor: NeighborId,
        period: String? = null,
    ) = CheckOutTool(loan, tool, neighbor, period?.let(::BigDecimal))

    private fun rejectionOf(t: Throwable): CommandRejected? =
        generateSequence(t) { it.cause }.filterIsInstance<CommandRejected>().firstOrNull()

    private fun assertRefused(
        reason: String,
        error: Throwable,
    ) {
        val rejected = rejectionOf(error)
        assertTrue(rejected != null, "expected a refusal, got $error")
        assertEquals(reason, rejected.reason)
    }

    // --- INV-CHK-6: the due date is set at checkout ---

    @Test
    fun `INV-CHK-6 - given drill available, when ana keeps the default, then recorded for 7 days due 12 October`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .`when`()
            .command(checkout(loan, drill, ana))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now))
    }

    @Test
    fun `INV-CHK-6 - given drill available, when ana chooses 3 days, then recorded for 3 days due 8 October`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .`when`()
            .command(checkout(loan, drill, ana, "3"))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ana, 3, LocalDate.of(2026, 10, 8), now))
    }

    @Test
    fun `INV-CHK-6 - given drill available, when ana chooses 1 day, then recorded for 1 day due 6 October`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .`when`()
            .command(checkout(loan, drill, ana, "1"))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ana, 1, LocalDate.of(2026, 10, 6), now))
    }

    @Test
    fun `INV-CHK-6 - given drill available, when ana chooses 7 days, then recorded for 7 days due 12 October`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .`when`()
            .command(checkout(loan, drill, ana, "7"))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now))
    }

    @Test
    fun `INV-CHK-6 - given drill available, when ana asks for 8 days, then refused period-out-of-range`() {
        world()
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ana, "8"))
            .then()
            .exceptionSatisfies { assertRefused("period-out-of-range", it) }
    }

    @Test
    fun `INV-CHK-6 - given drill available, when ana asks for 0 days, then refused period-out-of-range`() {
        world()
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ana, "0"))
            .then()
            .exceptionSatisfies { assertRefused("period-out-of-range", it) }
    }

    @Test
    fun `INV-CHK-6 - given drill available, when ana asks for 2_5 days, then refused period-out-of-range`() {
        world()
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ana, "2.5"))
            .then()
            .exceptionSatisfies { assertRefused("period-out-of-range", it) }
    }

    // --- INV-CHK-2: one loan at a time ---

    @Test
    fun `INV-CHK-2 - given ben has the drill on loan, when ana checks it out, then refused tool-checked-out`() {
        world()
            .event(ToolCheckedOut(LoanId(UUID.randomUUID()), drill, ben, 7, LocalDate.of(2026, 10, 12), now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ana))
            .then()
            .exceptionSatisfies { assertRefused("tool-checked-out", it) }
    }

    // --- INV-CHK-4: retired tools ---

    @Test
    fun `INV-CHK-4 - given the drill has been retired, when ana checks it out, then refused tool-retired`() {
        world()
            .event(ToolRetired(drill, now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ana))
            .then()
            .exceptionSatisfies { assertRefused("tool-retired", it) }
    }

    @Test
    fun `INV-CHK-4 - given the drill was recorded lost, when ana checks it out, then refused tool-retired`() {
        val lostLoan = LoanId(UUID.randomUUID())
        world()
            .event(ToolCheckedOut(lostLoan, drill, ben, 7, LocalDate.of(2026, 10, 12), now))
            .event(ToolLost(lostLoan, drill, now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ana))
            .then()
            .exceptionSatisfies { assertRefused("tool-retired", it) }
    }

    // --- INV-CHK-11: unknown tool or neighbor ---

    @Test
    fun `INV-CHK-11 - given no tool T9 has been added, when ana checks it out, then refused tool-not-found`() {
        world()
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), ToolId(UUID.randomUUID()), ana))
            .then()
            .exceptionSatisfies { assertRefused("tool-not-found", it) }
    }

    @Test
    fun `INV-CHK-11 - given no account N9 has been registered, when a checkout for N9 arrives, then refused neighbor-not-found`() {
        world()
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, NeighborId(UUID.randomUUID())))
            .then()
            .exceptionSatisfies { assertRefused("neighbor-not-found", it) }
    }

    // --- INV-CHK-7: suspended and closed accounts ---

    @Test
    fun `INV-CHK-7 - given ana is suspended, when ana checks out the drill, then refused neighbor-suspended`() {
        world()
            .event(NeighborSuspended(ana, now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ana))
            .then()
            .exceptionSatisfies { assertRefused("neighbor-suspended", it) }
    }

    @Test
    fun `INV-CHK-7 - given ana's account is closed, when a checkout for ana arrives, then refused account-closed`() {
        world()
            .event(NeighborAccountClosed(ana, false, now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ana))
            .then()
            .exceptionSatisfies { assertRefused("account-closed", it) }
    }

    @Test
    fun `INV-CHK-7 - given ana was suspended and reinstated, when ana checks out the drill, then recorded`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .event(NeighborSuspended(ana, now))
            .event(NeighborReinstated(ana, now))
            .`when`()
            .command(checkout(loan, drill, ana))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now))
    }

    // --- INV-CHK-5: overdue loans ---

    @Test
    fun `INV-CHK-5 - given ana has the ladder due 4 October, when ana checks out the drill on 5 October, then refused has-overdue-loan`() {
        world()
            .event(ToolCheckedOut(LoanId(UUID.randomUUID()), ladder, ana, 7, LocalDate.of(2026, 10, 4), now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ana))
            .then()
            .exceptionSatisfies { assertRefused("has-overdue-loan", it) }
    }

    @Test
    fun `INV-CHK-5 - given ana has the ladder due 5 October, when ana checks out the drill on 5 October, then recorded`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .event(ToolCheckedOut(LoanId(UUID.randomUUID()), ladder, ana, 7, today, now))
            .`when`()
            .command(checkout(loan, drill, ana))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now))
    }

    @Test
    fun `INV-CHK-5 - given ana's ladder loan was extended to 11 October, when ana checks out the drill on 5 October, then recorded`() {
        val ladderLoan = LoanId(UUID.randomUUID())
        val loan = LoanId(UUID.randomUUID())
        world()
            .event(ToolCheckedOut(ladderLoan, ladder, ana, 7, LocalDate.of(2026, 10, 4), now))
            .event(LoanExtended(ladderLoan, LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 11), now))
            .`when`()
            .command(checkout(loan, drill, ana))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now))
    }

    @Test
    fun `INV-CHK-5 - given ana's ladder loan was ended by loss, when ana checks out the drill on 5 October, then recorded`() {
        val ladderLoan = LoanId(UUID.randomUUID())
        val loan = LoanId(UUID.randomUUID())
        world()
            .event(ToolCheckedOut(ladderLoan, ladder, ana, 7, LocalDate.of(2026, 10, 1), now))
            .event(ToolLost(ladderLoan, ladder, now))
            .`when`()
            .command(checkout(loan, drill, ana))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now))
    }

    // --- INV-CHK-8: no loan limit ---

    @Test
    fun `INV-CHK-8 - given ana has two other loans, when ana checks out the drill, then recorded`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .event(ToolCheckedOut(LoanId(UUID.randomUUID()), ladder, ana, 7, today.plusDays(3), now))
            .event(ToolCheckedOut(LoanId(UUID.randomUUID()), ToolId(UUID.randomUUID()), ana, 7, today.plusDays(3), now))
            .`when`()
            .command(checkout(loan, drill, ana))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now))
    }

    // --- INV-CHK-12: idempotency by loan ID ---

    @Test
    fun `INV-CHK-12 - given loan L1 recorded for ana, when the same request arrives again, then succeeds with nothing recorded`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .event(ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now))
            .`when`()
            .command(checkout(loan, drill, ana))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-CHK-12 - given loan L1 recorded for the drill and ana, when L1 is requested for the ladder, then refused loan-id-in-use`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .event(ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now))
            .`when`()
            .command(checkout(loan, ladder, ana))
            .then()
            .exceptionSatisfies { assertRefused("loan-id-in-use", it) }
    }

    // --- INV-CHK-3 and INV-CHK-9: set-aside for a holder ---

    @Test
    fun `INV-CHK-3 - set aside for ben, ana checks out, refused set-aside-for-another`() {
        world()
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldFulfilled(hold1, drill, ben, LocalDate.of(2026, 10, 6), now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ana))
            .then()
            .exceptionSatisfies { assertRefused("set-aside-for-another", it) }
    }

    @Test
    fun `INV-CHK-3 INV-CHK-9 - set aside for ben, ben checks out, recorded and his hold ends`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldFulfilled(hold1, drill, ben, LocalDate.of(2026, 10, 6), now))
            .`when`()
            .command(checkout(loan, drill, ben))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ben, 7, LocalDate.of(2026, 10, 12), now))
    }

    @Test
    fun `INV-CHK-3 - given the drill set aside for ben to collect by 5 October, when ben checks out on 5 October, then recorded`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldFulfilled(hold1, drill, ben, today, now))
            .`when`()
            .command(checkout(loan, drill, ben))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ben, 7, LocalDate.of(2026, 10, 12), now))
    }

    @Test
    fun `INV-CHK-3 INV-CHK-5 - set aside for ben with an overdue ladder, ben checks out, refused has-overdue-loan`() {
        world()
            .event(ToolCheckedOut(LoanId(UUID.randomUUID()), ladder, ben, 7, LocalDate.of(2026, 10, 2), now))
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldFulfilled(hold1, drill, ben, LocalDate.of(2026, 10, 6), now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ben))
            .then()
            .exceptionSatisfies { assertRefused("has-overdue-loan", it) }
    }

    @Test
    fun `INV-CHK-7 - given the drill set aside for ben and ben is suspended, when ben checks out, then refused neighbor-suspended`() {
        world()
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldFulfilled(hold1, drill, ben, LocalDate.of(2026, 10, 6), now))
            .event(NeighborSuspended(ben, now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ben))
            .then()
            .exceptionSatisfies { assertRefused("neighbor-suspended", it) }
    }

    // --- INV-CHK-3a: the front of the queue ---

    @Test
    fun `INV-CHK-3a INV-CHK-9 - ben at the front before Hold Fulfilled, ben checks out, recorded and his hold ends`() {
        val loan = LoanId(UUID.randomUUID())
        val oldLoan = LoanId(UUID.randomUUID())
        world()
            .event(ToolCheckedOut(oldLoan, drill, cara, 7, today.minusDays(1), now))
            .event(ToolReturned(oldLoan, drill, now, today, false))
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldPlaced(hold2, drill, cara, now))
            .`when`()
            .command(checkout(loan, drill, ben))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ben, 7, LocalDate.of(2026, 10, 12), now))
    }

    @Test
    fun `INV-CHK-3a - given ben at the front and cara behind, when cara checks out, then refused reserved-for-another`() {
        world()
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldPlaced(hold2, drill, cara, now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, cara))
            .then()
            .exceptionSatisfies { assertRefused("reserved-for-another", it) }
    }

    @Test
    fun `INV-CHK-3a - given ben at the front, when ana checks out, then refused reserved-for-another`() {
        world()
            .event(HoldPlaced(hold1, drill, ben, now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ana))
            .then()
            .exceptionSatisfies { assertRefused("reserved-for-another", it) }
    }

    @Test
    fun `INV-CHK-3a INV-CHK-9 - ben returned an early checkout with cara waiting, ben checks out again, refused reserved-for-another`() {
        val firstLoan = LoanId(UUID.randomUUID())
        world()
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldPlaced(hold2, drill, cara, now))
            .event(ToolCheckedOut(firstLoan, drill, ben, 7, LocalDate.of(2026, 10, 12), now))
            .event(ToolReturned(firstLoan, drill, now, today, false))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ben))
            .then()
            .exceptionSatisfies { assertRefused("reserved-for-another", it) }
    }

    // --- INV-CHK-3b: lapsed set-asides, on 18 October ---

    private val lapsedDay = LocalDate.of(2026, 10, 18)
    private val lapsedNow: Instant = lapsedDay.atTime(9, 0).atZone(zone).toInstant()

    @Test
    fun `INV-CHK-3b - lapsed set-aside with cara waiting, ben checks out on 18 October, refused pickup-window-ended`() {
        world(lapsedDay)
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldFulfilled(hold1, drill, ben, LocalDate.of(2026, 10, 17), now))
            .event(HoldPlaced(hold2, drill, cara, now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ben))
            .then()
            .exceptionSatisfies { assertRefused("pickup-window-ended", it) }
    }

    @Test
    fun `INV-CHK-3b INV-CHK-3a - lapsed set-aside with cara waiting, ana checks out on 18 October, refused reserved-for-another`() {
        world(lapsedDay)
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldFulfilled(hold1, drill, ben, LocalDate.of(2026, 10, 17), now))
            .event(HoldPlaced(hold2, drill, cara, now))
            .`when`()
            .command(checkout(LoanId(UUID.randomUUID()), drill, ana))
            .then()
            .exceptionSatisfies { assertRefused("reserved-for-another", it) }
    }

    @Test
    fun `INV-CHK-3b INV-CHK-3a - lapsed set-aside with cara waiting, cara checks out on 18 October, recorded and her hold ends`() {
        val loan = LoanId(UUID.randomUUID())
        world(lapsedDay)
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldFulfilled(hold1, drill, ben, LocalDate.of(2026, 10, 17), now))
            .event(HoldPlaced(hold2, drill, cara, now))
            .`when`()
            .command(checkout(loan, drill, cara))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, cara, 7, LocalDate.of(2026, 10, 25), lapsedNow))
    }

    @Test
    fun `INV-CHK-3b - given ben's set-aside lapsed with no hold waiting, when ana checks out on 18 October, then recorded`() {
        val loan = LoanId(UUID.randomUUID())
        world(lapsedDay)
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldFulfilled(hold1, drill, ben, LocalDate.of(2026, 10, 17), now))
            .`when`()
            .command(checkout(loan, drill, ana))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 25), lapsedNow))
    }

    @Test
    fun `INV-CHK-3b INV-CHK-9 - given ben's set-aside lapsed with no hold waiting, when ben checks out on 18 October, then recorded`() {
        val loan = LoanId(UUID.randomUUID())
        world(lapsedDay)
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldFulfilled(hold1, drill, ben, LocalDate.of(2026, 10, 17), now))
            .`when`()
            .command(checkout(loan, drill, ben))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ben, 7, LocalDate.of(2026, 10, 25), lapsedNow))
    }

    // --- a closed holder's holds end (close-account.md) ---

    @Test
    fun `INV-CHK-3a INV-CHK-7 - given ben at the front and cara behind, when ben closes his account, then cara checks out`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldPlaced(hold2, drill, cara, now))
            .event(NeighborAccountClosed(ben, false, now))
            .`when`()
            .command(checkout(loan, drill, cara))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, cara, 7, LocalDate.of(2026, 10, 12), now))
    }

    @Test
    fun `INV-CHK-3 INV-CHK-7 - given the drill set aside for ben who then closes, when ana checks out, then recorded`() {
        val loan = LoanId(UUID.randomUUID())
        world()
            .event(HoldPlaced(hold1, drill, ben, now))
            .event(HoldFulfilled(hold1, drill, ben, today, now))
            .event(NeighborAccountClosed(ben, false, now))
            .`when`()
            .command(checkout(loan, drill, ana))
            .then()
            .success()
            .events(ToolCheckedOut(loan, drill, ana, 7, LocalDate.of(2026, 10, 12), now))
    }
}
