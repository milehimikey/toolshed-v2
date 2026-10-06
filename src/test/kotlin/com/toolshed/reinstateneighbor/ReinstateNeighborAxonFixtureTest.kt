package com.toolshed.reinstateneighbor

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.NeighborId
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
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

/** Scenario tests for Reinstate Neighbor, through AxonTestFixture built from the slice's configure function. */
class ReinstateNeighborAxonFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val today = LocalDate.of(2026, 10, 5)
    private val now: Instant = today.atTime(9, 0).atZone(zone).toInstant()

    private var fixture: AxonTestFixture? = null

    private val n1 = NeighborId(UUID.randomUUID())
    private val n2 = NeighborId(UUID.randomUUID())
    private val n3 = NeighborId(UUID.randomUUID())
    private val n9 = NeighborId(UUID.randomUUID())

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    private fun neighbors(): AxonTestPhase.Given {
        fixture?.stop()
        val configurer = ReinstateNeighborConfiguration.configure(EventSourcingConfigurer.create(), Clock.fixed(now, zone))
        val built = AxonTestFixture.with(configurer)
        fixture = built
        return built
            .given()
            .event(NeighborRegistered(n1, "Ana", "ana@example.com", now))
            .event(NeighborRegistered(n2, "Ben", "ben@example.com", now))
            .event(NeighborRegistered(n3, "Cy", "cy@example.com", now))
    }

    private fun reinstate(neighbor: NeighborId) = ReinstateNeighbor(neighbor)

    private fun assertRefused(
        reason: String,
        error: Throwable,
    ) {
        val rejected = generateSequence(error) { it.cause }.filterIsInstance<CommandRejected>().firstOrNull()
        assertTrue(rejected != null, "expected a refusal, got $error")
        assertEquals(reason, rejected.reason)
    }

    @Test
    fun `INV-REI-2 - given N1 is suspended, when reinstated, then Neighbor Reinstated is recorded`() {
        neighbors()
            .event(NeighborSuspended(n1, now))
            .`when`()
            .command(reinstate(n1))
            .then()
            .success()
            .events(NeighborReinstated(n1, now))
    }

    @Test
    fun `INV-REI-7 - given N1 is already active, when reinstated again, then nothing recorded`() {
        neighbors()
            .event(NeighborSuspended(n1, now))
            .event(NeighborReinstated(n1, now))
            .`when`()
            .command(reinstate(n1))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-REI-7 - given Ben was never suspended, when reinstated, then nothing recorded`() {
        neighbors()
            .`when`()
            .command(reinstate(n2))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-REI-2 - given N1 was reinstated and suspended again, when a late copy arrives, then reinstated again`() {
        neighbors()
            .event(NeighborSuspended(n1, now))
            .event(NeighborReinstated(n1, now))
            .event(NeighborSuspended(n1, now))
            .`when`()
            .command(reinstate(n1))
            .then()
            .success()
            .events(NeighborReinstated(n1, now))
    }

    @Test
    fun `INV-REI-2 - given N3 was suspended and then closed, when reinstated, then refused account-closed`() {
        neighbors()
            .event(NeighborSuspended(n3, now))
            .event(NeighborAccountClosed(n3, true, now))
            .`when`()
            .command(reinstate(n3))
            .then()
            .exceptionSatisfies { assertRefused("account-closed", it) }
    }

    @Test
    fun `INV-REI-8 - given no account has N9, when reinstated, then refused neighbor-not-found`() {
        neighbors()
            .`when`()
            .command(reinstate(n9))
            .then()
            .exceptionSatisfies { assertRefused("neighbor-not-found", it) }
    }
}
