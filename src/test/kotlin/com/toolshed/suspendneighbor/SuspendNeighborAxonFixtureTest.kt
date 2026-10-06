package com.toolshed.suspendneighbor

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.flagneighbor.NeighborFlagged
import com.toolshed.placehold.HoldPlaced
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
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

/** Scenario tests for Suspend Neighbor, through AxonTestFixture built from the slice's configure function. */
class SuspendNeighborAxonFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val today = LocalDate.of(2026, 10, 5)
    private val now: Instant = today.atTime(9, 0).atZone(zone).toInstant()

    private var fixture: AxonTestFixture? = null

    private val n1 = NeighborId(UUID.randomUUID())
    private val n3 = NeighborId(UUID.randomUUID())
    private val n4 = NeighborId(UUID.randomUUID())
    private val drill = ToolId(UUID.randomUUID())
    private val ladder = ToolId(UUID.randomUUID())
    private val ladderLoan = LoanId(UUID.randomUUID())
    private val hold = HoldId(UUID.randomUUID())

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    private fun world(): AxonTestPhase.Given {
        fixture?.stop()
        val configurer = SuspendNeighborConfiguration.configure(EventSourcingConfigurer.create(), Clock.fixed(now, zone))
        val built = AxonTestFixture.with(configurer)
        fixture = built
        return built
            .given()
            .event(NeighborRegistered(n1, "Ana", "ana@example.com", now))
            .event(NeighborRegistered(n3, "Cy", "cy@example.com", now))
            .event(NeighborRegistered(n4, "Dee", "dee@example.com", now))
    }

    private fun suspend(neighbor: NeighborId) = SuspendNeighbor(neighbor)

    private fun assertRefused(
        reason: String,
        error: Throwable,
    ) {
        val rejected = generateSequence(error) { it.cause }.filterIsInstance<CommandRejected>().firstOrNull()
        assertTrue(rejected != null, "expected a refusal, got $error")
        assertEquals(reason, rejected.reason)
    }

    @Test
    fun `INV-SUS-1 INV-SUS-2 - given Ana flagged with a loan and a hold, when suspended, then only Neighbor Suspended`() {
        world()
            .event(NeighborFlagged(n1, listOf(ladderLoan), now))
            .event(ToolCheckedOut(ladderLoan, ladder, n1, 7, today.plusDays(7), now))
            .event(HoldPlaced(hold, drill, n1, now))
            .`when`()
            .command(suspend(n1))
            .then()
            .success()
            .events(NeighborSuspended(n1, now))
    }

    @Test
    fun `INV-SUS-1 INV-SUS-2 - given Ben active and not flagged, when suspended, then recorded`() {
        world()
            .`when`()
            .command(suspend(n3))
            .then()
            .success()
            .events(NeighborSuspended(n3, now))
    }

    @Test
    fun `INV-SUS-2 - given Ana was suspended and reinstated, when suspended again, then a new Neighbor Suspended is recorded`() {
        world()
            .event(NeighborSuspended(n1, now))
            .event(NeighborReinstated(n1, now))
            .`when`()
            .command(suspend(n1))
            .then()
            .success()
            .events(NeighborSuspended(n1, now))
    }

    @Test
    fun `INV-SUS-9 - given Ana is suspended, when suspended again, then succeeds with nothing recorded`() {
        world()
            .event(NeighborSuspended(n1, now))
            .`when`()
            .command(suspend(n1))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-SUS-2 - given Ana was suspended then reinstated, when a delayed copy of the suspend request arrives, then suspended again`() {
        world()
            .event(NeighborSuspended(n1, now))
            .event(NeighborReinstated(n1, now))
            .`when`()
            .command(suspend(n1))
            .then()
            .success()
            .events(NeighborSuspended(n1, now))
    }

    @Test
    fun `INV-SUS-2 - given Cy's account is closed, when suspended, then refused account-closed`() {
        world()
            .event(NeighborAccountClosed(n3, false, now))
            .`when`()
            .command(suspend(n3))
            .then()
            .exceptionSatisfies { assertRefused("account-closed", it) }
    }

    @Test
    fun `INV-SUS-2 - given Dee was suspended and then closed her account, when suspended, then refused account-closed and not a repeat`() {
        world()
            .event(NeighborSuspended(n4, now))
            .event(NeighborAccountClosed(n4, true, now))
            .`when`()
            .command(suspend(n4))
            .then()
            .exceptionSatisfies { assertRefused("account-closed", it) }
    }

    @Test
    fun `INV-SUS-10 - given no account N9 has been registered, when suspended, then refused neighbor-not-found`() {
        world()
            .`when`()
            .command(suspend(NeighborId(UUID.randomUUID())))
            .then()
            .exceptionSatisfies { assertRefused("neighbor-not-found", it) }
    }
}
