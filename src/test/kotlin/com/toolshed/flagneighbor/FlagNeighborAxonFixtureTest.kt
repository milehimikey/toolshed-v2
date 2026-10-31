package com.toolshed.flagneighbor

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
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

/** Scenario tests for Flag Neighbor through AxonTestFixture built from the slice's configure function. */
class FlagNeighborAxonFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val today = LocalDate.of(2026, 10, 5)
    private val now: Instant = today.atTime(9, 0).atZone(zone).toInstant()

    private var fixture: AxonTestFixture? = null

    private val n1 = NeighborId(UUID.nameUUIDFromBytes("n1".toByteArray()))
    private val n2 = NeighborId(UUID.nameUUIDFromBytes("n2".toByteArray()))
    private val n9 = NeighborId(UUID.nameUUIDFromBytes("n9".toByteArray()))
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val l2 = LoanId(UUID.nameUUIDFromBytes("l2".toByteArray()))
    private val l3 = LoanId(UUID.nameUUIDFromBytes("l3".toByteArray()))
    private val l4 = LoanId(UUID.nameUUIDFromBytes("l4".toByteArray()))
    private val l5 = LoanId(UUID.nameUUIDFromBytes("l5".toByteArray()))
    private val l6 = LoanId(UUID.nameUUIDFromBytes("l6".toByteArray()))
    private val l7 = LoanId(UUID.nameUUIDFromBytes("l7".toByteArray()))

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    private fun onDay(day: LocalDate): Clock = Clock.fixed(day.atTime(9, 0).atZone(zone).toInstant(), zone)

    /** Ana (N1) is registered and active; the clock reads [day] (5 October by default). */
    private fun ana(day: LocalDate = today): AxonTestPhase.Given {
        fixture?.stop()
        val built = AxonTestFixture.with(FlagNeighborConfiguration.configure(EventSourcingConfigurer.create(), onDay(day)))
        fixture = built
        return built
            .given()
            .event(NeighborRegistered(n1, "Ana", "ana@example.com", now))
            .event(NeighborRegistered(n2, "Bo", "bo@example.com", now))
    }

    private fun AxonTestPhase.Given.lateReturn(
        loan: LoanId,
        on: LocalDate,
        by: NeighborId = n1,
    ): AxonTestPhase.Given =
        event(ToolCheckedOut(loan, drill, by, 7, on.minusDays(1), now))
            .event(ToolReturned(loan, drill, now, on, true))

    private fun AxonTestPhase.Given.threeLate(): AxonTestPhase.Given =
        lateReturn(l1, LocalDate.of(2026, 9, 1)).lateReturn(l2, LocalDate.of(2026, 9, 20)).lateReturn(l3, today)

    private fun assertRefused(
        reason: String,
        error: Throwable,
    ) {
        val rejected = generateSequence(error) { it.cause }.filterIsInstance<CommandRejected>().firstOrNull()
        assertTrue(rejected != null, "expected a refusal, got $error")
        assertEquals(reason, rejected.reason)
    }

    @Test
    fun `INV-FLG-2 INV-FLG-3 INV-FLG-4 - given three counting late returns, when flagged, then only Neighbor Flagged is recorded`() {
        ana()
            .threeLate()
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2, l3)))
            .then()
            .success()
            .events(NeighborFlagged(n1, listOf(l1, l2, l3), now))
    }

    @Test
    fun `INV-FLG-4 - given four counting late returns, when flagged with all four, then all four are named`() {
        ana()
            .threeLate()
            .lateReturn(l4, today)
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2, l3, l4)))
            .then()
            .success()
            .events(NeighborFlagged(n1, listOf(l1, l2, l3, l4), now))
    }

    @Test
    fun `INV-FLG-4 - given four counting late returns, when flagged with only three, then refused late-returns-changed`() {
        ana()
            .threeLate()
            .lateReturn(l4, today)
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2, l3)))
            .then()
            .exceptionSatisfies { assertRefused("late-returns-changed", it) }
    }

    @Test
    fun `INV-FLG-2 - given two counting late returns, when flagged, then refused below-threshold`() {
        ana()
            .lateReturn(l1, LocalDate.of(2026, 9, 20))
            .lateReturn(l2, today)
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2)))
            .then()
            .exceptionSatisfies { assertRefused("below-threshold", it) }
    }

    @Test
    fun `INV-FLG-2 - given a late return from 1 June, when flagged on 5 October, then refused below-threshold`() {
        ana()
            .lateReturn(l1, LocalDate.of(2026, 6, 1))
            .lateReturn(l2, LocalDate.of(2026, 9, 20))
            .lateReturn(l3, today)
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2, l3)))
            .then()
            .exceptionSatisfies { assertRefused("below-threshold", it) }
    }

    @Test
    fun `INV-FLG-2 - given a late return from 8 July, when flagged on 5 October, then recorded since it is the 89th day`() {
        ana()
            .lateReturn(l1, LocalDate.of(2026, 7, 8))
            .lateReturn(l2, LocalDate.of(2026, 9, 20))
            .lateReturn(l3, today)
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2, l3)))
            .then()
            .success()
            .events(NeighborFlagged(n1, listOf(l1, l2, l3), now))
    }

    @Test
    fun `INV-FLG-4 INV-FLG-7 - given Ana was flagged and returned three more late, when flagged again, then recorded again`() {
        val nov15 = LocalDate.of(2026, 11, 15)
        val flaggedAt = now.minusSeconds(60)
        ana(nov15)
            .threeLate()
            .event(NeighborFlagged(n1, listOf(l1, l2, l3), flaggedAt))
            .lateReturn(l5, LocalDate.of(2026, 10, 20))
            .lateReturn(l6, LocalDate.of(2026, 11, 1))
            .lateReturn(l7, nov15)
            .`when`()
            .command(FlagNeighbor(n1, listOf(l5, l6, l7)))
            .then()
            .success()
            .events(NeighborFlagged(n1, listOf(l5, l6, l7), nov15.atTime(9, 0).atZone(zone).toInstant()))
    }

    @Test
    fun `INV-FLG-2 INV-FLG-4 - given L1 to L3 were flagged and L5 returned late, when all four are named, then refused below-threshold`() {
        ana()
            .threeLate()
            .event(NeighborFlagged(n1, listOf(l1, l2, l3), now))
            .lateReturn(l5, today)
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2, l3, l5)))
            .then()
            .exceptionSatisfies { assertRefused("below-threshold", it) }
    }

    @Test
    fun `INV-FLG-4 - given L1 to L3 flagged and L5 to L7 returned late, when L1 is named with them, then late-returns-changed`() {
        ana()
            .threeLate()
            .event(NeighborFlagged(n1, listOf(l1, l2, l3), now))
            .lateReturn(l5, today)
            .lateReturn(l6, today)
            .lateReturn(l7, today)
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l5, l6, l7)))
            .then()
            .exceptionSatisfies { assertRefused("late-returns-changed", it) }
    }

    @Test
    fun `INV-FLG-4 - given another neighbor late returns, when Ana is flagged with them, then below-threshold`() {
        ana()
            .lateReturn(l1, today, by = n2)
            .lateReturn(l2, today, by = n2)
            .lateReturn(l3, today, by = n2)
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2, l3)))
            .then()
            .exceptionSatisfies { assertRefused("below-threshold", it) }
    }

    @Test
    fun `INV-FLG-9 - given Neighbor Flagged names L1 L2 L3, when the same flag arrives again, then it succeeds and records nothing`() {
        ana()
            .threeLate()
            .event(NeighborFlagged(n1, listOf(l1, l2, l3), now))
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2, l3)))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-FLG-5 - given Ana is suspended with three unflagged late returns, when flagged, then refused neighbor-suspended`() {
        ana()
            .threeLate()
            .event(NeighborSuspended(n1, now))
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2, l3)))
            .then()
            .exceptionSatisfies { assertRefused("neighbor-suspended", it) }
    }

    @Test
    fun `INV-FLG-5 - given three late returns then suspension and reinstatement, when flagged, then refused below-threshold`() {
        ana()
            .threeLate()
            .event(NeighborSuspended(n1, now))
            .event(NeighborReinstated(n1, now))
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2, l3)))
            .then()
            .exceptionSatisfies { assertRefused("below-threshold", it) }
    }

    @Test
    fun `INV-FLG-5 - given late returns made while suspended, when Ana is reinstated and flagged, then they do not count`() {
        ana()
            .event(NeighborSuspended(n1, now))
            .threeLate()
            .event(NeighborReinstated(n1, now))
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2, l3)))
            .then()
            .exceptionSatisfies { assertRefused("below-threshold", it) }
    }

    @Test
    fun `INV-FLG-5 - given Ana's account was closed after three late returns, when flagged, then refused account-closed`() {
        ana()
            .threeLate()
            .event(NeighborAccountClosed(n1, false, now))
            .`when`()
            .command(FlagNeighbor(n1, listOf(l1, l2, l3)))
            .then()
            .exceptionSatisfies { assertRefused("account-closed", it) }
    }

    @Test
    fun `INV-FLG-10 - given no account N9, when N9 is flagged, then refused neighbor-not-found`() {
        ana()
            .`when`()
            .command(FlagNeighbor(n9, listOf(l1, l2, l3)))
            .then()
            .exceptionSatisfies { assertRefused("neighbor-not-found", it) }
    }
}
