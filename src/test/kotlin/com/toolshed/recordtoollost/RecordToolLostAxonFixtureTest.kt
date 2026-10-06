package com.toolshed.recordtoollost

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.placehold.HoldPlaced
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
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

/** Scenario tests for Record Tool Lost, through AxonTestFixture built from the slice's configure function. */
class RecordToolLostAxonFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val now: Instant =
        LocalDate
            .of(2026, 10, 20)
            .atTime(9, 0)
            .atZone(zone)
            .toInstant()

    private var fixture: AxonTestFixture? = null

    private val t1 = ToolId(UUID.randomUUID())
    private val t2 = ToolId(UUID.randomUUID())
    private val t3 = ToolId(UUID.randomUUID())
    private val ana = NeighborId(UUID.randomUUID())
    private val ben = NeighborId(UUID.randomUUID())
    private val l2 = LoanId(UUID.randomUUID())
    private val l3 = LoanId(UUID.randomUUID())
    private val l4 = LoanId(UUID.randomUUID())
    private val h1 = HoldId(UUID.randomUUID())

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    private fun world(): AxonTestPhase.Given {
        fixture?.stop()
        val configurer = RecordToolLostConfiguration.configure(EventSourcingConfigurer.create(), Clock.fixed(now, zone))
        val built = AxonTestFixture.with(configurer)
        fixture = built
        return built.given()
    }

    private fun assertRefused(
        reason: String,
        error: Throwable,
    ) {
        val rejected = generateSequence(error) { it.cause }.filterIsInstance<CommandRejected>().firstOrNull()
        assertTrue(rejected != null, "expected a refusal, got $error")
        assertEquals(reason, rejected.reason)
    }

    @Test
    fun `INV-LOST-1 INV-LOST-2 INV-LOST-3 - given Ana has the ladder overdue and Ben waiting, when recorded lost, then only Tool Lost`() {
        world()
            .event(ToolCheckedOut(l2, t2, ana, 7, LocalDate.of(2026, 10, 12), now))
            .event(HoldPlaced(h1, t2, ben, now))
            .`when`()
            .command(RecordToolLost(l2, t2))
            .then()
            .success()
            .events(ToolLost(l2, t2, now))
    }

    @Test
    fun `INV-LOST-2 - given Ana has the hedge trimmer due 25 October, when recorded lost on 15 October, then recorded`() {
        world()
            .event(ToolCheckedOut(l3, t3, ana, 7, LocalDate.of(2026, 10, 25), now))
            .`when`()
            .command(RecordToolLost(l3, t3))
            .then()
            .success()
            .events(ToolLost(l3, t3, now))
    }

    @Test
    fun `INV-LOST-2 - given the drill was retired while Ben had it on loan, when recorded lost, then recorded against that loan`() {
        world()
            .event(ToolCheckedOut(l4, t1, ben, 7, LocalDate.of(2026, 10, 12), now))
            .event(ToolRetired(t1, now))
            .`when`()
            .command(RecordToolLost(l4, t1))
            .then()
            .success()
            .events(ToolLost(l4, t1, now))
    }

    @Test
    fun `INV-LOST-10 - given Tool Lost was recorded for L2, when the same request arrives again, then succeeds with nothing recorded`() {
        world()
            .event(ToolCheckedOut(l2, t2, ana, 7, LocalDate.of(2026, 10, 12), now))
            .event(ToolLost(l2, t2, now))
            .`when`()
            .command(RecordToolLost(l2, t2))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-LOST-2 - given Tool Returned was recorded for L5, when recorded lost, then refused loan-ended`() {
        val l5 = LoanId(UUID.randomUUID())
        world()
            .event(ToolCheckedOut(l5, t2, ana, 7, LocalDate.of(2026, 10, 12), now))
            .event(ToolReturned(l5, t2, now, LocalDate.of(2026, 10, 20), false))
            .`when`()
            .command(RecordToolLost(l5, t2))
            .then()
            .exceptionSatisfies { assertRefused("loan-ended", it) }
    }

    @Test
    fun `INV-LOST-11 - given Ana has the ladder out, when the request names another tool, then refused loan-not-found`() {
        world()
            .event(ToolCheckedOut(l2, t2, ana, 7, LocalDate.of(2026, 10, 12), now))
            .`when`()
            .command(RecordToolLost(l2, t3))
            .then()
            .exceptionSatisfies { assertRefused("loan-not-found", it) }
    }

    @Test
    fun `INV-LOST-11 - given no loan has ID L9, when recorded lost, then refused loan-not-found`() {
        world()
            .`when`()
            .command(RecordToolLost(LoanId(UUID.randomUUID()), t2))
            .then()
            .exceptionSatisfies { assertRefused("loan-not-found", it) }
    }
}
