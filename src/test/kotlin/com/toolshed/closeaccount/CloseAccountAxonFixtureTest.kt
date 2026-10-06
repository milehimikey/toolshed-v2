package com.toolshed.closeaccount

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
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.AxonTestPhase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Scenario tests for Close Account, through AxonTestFixture built from the slice's configure function. */
class CloseAccountAxonFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val today = LocalDate.of(2026, 10, 5)
    private val now: Instant = today.atTime(9, 0).atZone(zone).toInstant()

    private var fixture: AxonTestFixture? = null

    private val n1 = NeighborId(UUID.randomUUID())
    private val n3 = NeighborId(UUID.randomUUID())
    private val n9 = NeighborId(UUID.randomUUID())
    private val drill = ToolId(UUID.randomUUID())
    private val ladder = ToolId(UUID.randomUUID())
    private val hedge = ToolId(UUID.randomUUID())
    private val ladderLoan = LoanId(UUID.randomUUID())
    private val hedgeLoan = LoanId(UUID.randomUUID())
    private val hold = HoldId(UUID.randomUUID())

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    /** Ana (N1) is registered, active and has no tools on loan, unless the scenario says otherwise. */
    private fun ana(): AxonTestPhase.Given {
        fixture?.stop()
        val configurer = CloseAccountConfiguration.configure(EventSourcingConfigurer.create(), Clock.fixed(now, zone))
        val built = AxonTestFixture.with(configurer)
        fixture = built
        return built
            .given()
            .event(NeighborRegistered(n1, "Ana", "ana@example.com", now))
            .event(NeighborRegistered(n3, "Cy", "cy@example.com", now))
    }

    private fun close(neighbor: NeighborId) = CloseAccount(neighbor)

    private fun assertRefused(
        reason: String,
        error: Throwable,
    ) {
        val rejected = generateSequence(error) { it.cause }.filterIsInstance<CommandRejected>().firstOrNull()
        assertTrue(rejected != null, "expected a refusal, got $error")
        assertEquals(reason, rejected.reason)
    }

    @Test
    fun `INV-CLS-2 INV-CLS-9 - given Ana has a hold waiting on the drill, when she closes, then recorded with whileSuspended no`() {
        ana()
            .event(HoldPlaced(hold, drill, n1, now))
            .`when`()
            .command(close(n1))
            .then()
            .success()
            .events(NeighborAccountClosed(n1, false, now))
    }

    @Test
    fun `INV-CLS-2 INV-CLS-9 - given Ana is suspended, when she closes, then recorded with whileSuspended yes`() {
        ana()
            .event(NeighborSuspended(n1, now))
            .`when`()
            .command(close(n1))
            .then()
            .success()
            .events(NeighborAccountClosed(n1, true, now))
    }

    @Test
    fun `INV-CLS-9 - given Ana was suspended and has been reinstated, when she closes, then whileSuspended no`() {
        ana()
            .event(NeighborSuspended(n1, now))
            .event(NeighborReinstated(n1, now))
            .`when`()
            .command(close(n1))
            .then()
            .success()
            .events(NeighborAccountClosed(n1, false, now))
    }

    @Test
    fun `INV-CLS-2 - given the drill is set aside for Ana, when she closes, then recorded, since a set-aside is not a loan`() {
        ana()
            .event(HoldFulfilled(hold, drill, n1, today.plusDays(3), now))
            .`when`()
            .command(close(n1))
            .then()
            .success()
            .events(NeighborAccountClosed(n1, false, now))
    }

    @Test
    fun `INV-CLS-7 - given Ana has the ladder on loan, not overdue, when she closes, then refused tools-on-loan`() {
        ana()
            .event(ToolCheckedOut(ladderLoan, ladder, n1, 7, today.plusDays(7), now))
            .`when`()
            .command(close(n1))
            .then()
            .exceptionSatisfies { assertRefused("tools-on-loan", it) }
    }

    @Test
    fun `INV-CLS-7 - given Ana has the ladder on loan and it is overdue, when she closes, then refused tools-on-loan`() {
        ana()
            .event(ToolCheckedOut(ladderLoan, ladder, n1, 3, today.minusDays(2), now))
            .`when`()
            .command(close(n1))
            .then()
            .exceptionSatisfies { assertRefused("tools-on-loan", it) }
    }

    @Test
    fun `INV-CLS-7 - given Ana's only loan ended when the hedge trimmer was recorded lost, when she closes, then recorded`() {
        ana()
            .event(ToolCheckedOut(hedgeLoan, hedge, n1, 7, today.plusDays(7), now))
            .event(ToolLost(hedgeLoan, hedge, now))
            .`when`()
            .command(close(n1))
            .then()
            .success()
            .events(NeighborAccountClosed(n1, false, now))
    }

    @Test
    fun `INV-CLS-7 - given Ana had the ladder and has returned it, when she closes, then recorded`() {
        ana()
            .event(ToolCheckedOut(ladderLoan, ladder, n1, 7, today.plusDays(7), now))
            .event(ToolReturned(ladderLoan, ladder, now, today, false))
            .`when`()
            .command(close(n1))
            .then()
            .success()
            .events(NeighborAccountClosed(n1, false, now))
    }

    @Test
    fun `INV-CLS-2 - given Neighbor Account Closed has been recorded for Ana, when she closes again, then nothing new recorded`() {
        ana()
            .event(NeighborAccountClosed(n1, false, now))
            .`when`()
            .command(close(n1))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-CLS-8 - given no account N9 has been registered, when closed, then refused neighbor-not-found`() {
        ana()
            .`when`()
            .command(close(n9))
            .then()
            .exceptionSatisfies { assertRefused("neighbor-not-found", it) }
    }
}
