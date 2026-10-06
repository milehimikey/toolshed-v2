package com.toolshed.addtool

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.ToolId
import com.toolshed.retiretool.ToolRetired
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

/** Scenario tests for Add Tool, through AxonTestFixture built from the slice's configure function. */
class AddToolAxonFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val today = LocalDate.of(2026, 10, 5)
    private val now: Instant = today.atTime(9, 0).atZone(zone).toInstant()

    private var fixture: AxonTestFixture? = null

    private val t1 = ToolId(UUID.randomUUID())
    private val t2 = ToolId(UUID.randomUUID())

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    private fun shed(): AxonTestPhase.Given {
        fixture?.stop()
        val configurer = AddToolConfiguration.configure(EventSourcingConfigurer.create(), Clock.fixed(now, zone))
        val built = AxonTestFixture.with(configurer)
        fixture = built
        return built.given()
    }

    private fun add(
        tool: ToolId = t1,
        name: String? = "Cordless drill",
        description: String? = "18V, two batteries",
        days: String? = "7",
    ) = AddTool(tool, name, description, days?.let { BigDecimal(it) })

    private fun added(
        name: String,
        description: String?,
        days: Int,
        tool: ToolId = t1,
    ) = ToolAdded(tool, name, description, days, now)

    private fun assertRefused(
        reason: String,
        error: Throwable,
    ) {
        val rejected = generateSequence(error) { it.cause }.filterIsInstance<CommandRejected>().firstOrNull()
        assertTrue(rejected != null, "expected a refusal, got $error")
        assertEquals(reason, rejected.reason)
    }

    @Test
    fun `INV-ADD-9 - given no tools, when the shed manager adds cordless drill with 7 days, then Tool Added recorded`() {
        shed()
            .`when`()
            .command(add())
            .then()
            .success()
            .events(added("Cordless drill", "18V, two batteries", 7))
    }

    @Test
    fun `INV-ADD-4 - given no tools, when a ladder with 3 days and no description, then recorded with no description`() {
        shed()
            .`when`()
            .command(add(name = "Ladder", description = null, days = "3"))
            .then()
            .success()
            .events(added("Ladder", null, 3))
    }

    @Test
    fun `INV-ADD-4 - given no tools, when the description is only whitespace, then recorded with no description`() {
        shed()
            .`when`()
            .command(add(name = "Ladder", description = "   ", days = "3"))
            .then()
            .success()
            .events(added("Ladder", null, 3))
    }

    @Test
    fun `INV-ADD-4 - given no tools, when the name has surrounding whitespace, then recorded trimmed`() {
        shed()
            .`when`()
            .command(add(name = "  Ladder  ", description = null, days = "3"))
            .then()
            .success()
            .events(added("Ladder", null, 3))
    }

    @Test
    fun `INV-ADD-5 INV-ADD-8 INV-ADD-9 - given T1 is a cordless drill, when an identical drill T2 is added, then a separate tool`() {
        shed()
            .event(added("Cordless drill", "18V, two batteries", 7))
            .`when`()
            .command(add(tool = t2))
            .then()
            .success()
            .events(added("Cordless drill", "18V, two batteries", 7, t2))
    }

    @Test
    fun `INV-ADD-11 - given no tools, when name is 100 and description is 500 characters, then recorded`() {
        val name = "n".repeat(100)
        val description = "d".repeat(500)
        shed()
            .`when`()
            .command(add(name = name, description = description, days = "7"))
            .then()
            .success()
            .events(added(name, description, 7))
    }

    @Test
    fun `INV-ADD-11 - given no tools, when name is 101 characters, then refused name-too-long`() {
        shed()
            .`when`()
            .command(add(name = "n".repeat(101)))
            .then()
            .exceptionSatisfies { assertRefused("name-too-long", it) }
    }

    @Test
    fun `INV-ADD-11 - given no tools, when description is 501 characters, then refused description-too-long`() {
        shed()
            .`when`()
            .command(add(description = "d".repeat(501)))
            .then()
            .exceptionSatisfies { assertRefused("description-too-long", it) }
    }

    @Test
    fun `INV-ADD-3 - given no tools, when period is 1 day, then recorded`() {
        shed()
            .`when`()
            .command(add(name = "Stud finder", description = null, days = "1"))
            .then()
            .success()
            .events(added("Stud finder", null, 1))
    }

    @Test
    fun `INV-ADD-3 - given no tools, when period is 30 days, then recorded`() {
        shed()
            .`when`()
            .command(add(name = "Tile saw", description = null, days = "30"))
            .then()
            .success()
            .events(added("Tile saw", null, 30))
    }

    @Test
    fun `INV-ADD-3 - given no tools, when period is 31 days, then refused period-out-of-range`() {
        shed()
            .`when`()
            .command(add(name = "Tile saw", days = "31"))
            .then()
            .exceptionSatisfies { assertRefused("period-out-of-range", it) }
    }

    @Test
    fun `INV-ADD-3 - given no tools, when period is 0 days, then refused period-out-of-range`() {
        shed()
            .`when`()
            .command(add(days = "0"))
            .then()
            .exceptionSatisfies { assertRefused("period-out-of-range", it) }
    }

    @Test
    fun `INV-ADD-3 - given no tools, when period is two and a half days, then refused period-out-of-range`() {
        shed()
            .`when`()
            .command(add(days = "2.5"))
            .then()
            .exceptionSatisfies { assertRefused("period-out-of-range", it) }
    }

    @Test
    fun `INV-ADD-2 - given no tools, when no default period, then refused period-missing`() {
        shed()
            .`when`()
            .command(add(days = null))
            .then()
            .exceptionSatisfies { assertRefused("period-missing", it) }
    }

    @Test
    fun `INV-ADD-4 - given no tools, when the name is empty, then refused name-missing`() {
        shed()
            .`when`()
            .command(add(name = ""))
            .then()
            .exceptionSatisfies { assertRefused("name-missing", it) }
    }

    @Test
    fun `INV-ADD-4 - given no tools, when the name is only whitespace, then refused name-missing`() {
        shed()
            .`when`()
            .command(add(name = "   "))
            .then()
            .exceptionSatisfies { assertRefused("name-missing", it) }
    }

    @Test
    fun `INV-ADD-10 - given T1 was added, when the same request arrives again, then nothing recorded`() {
        shed()
            .event(added("Cordless drill", "18V, two batteries", 7))
            .`when`()
            .command(add())
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-ADD-10 - given T1 was added and retired, when the same request arrives again, then nothing recorded`() {
        shed()
            .event(added("Cordless drill", "18V, two batteries", 7))
            .event(ToolRetired(t1, now))
            .`when`()
            .command(add())
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-ADD-10 - given T1 is a ladder of 3 days, when a repeat differs only in whitespace, then nothing recorded`() {
        shed()
            .event(added("Ladder", null, 3))
            .`when`()
            .command(add(name = "  Ladder ", description = null, days = "3"))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-ADD-10 - given T1 is a cordless drill of 7 days, when T1 is reused for a ladder, then refused tool-id-in-use`() {
        shed()
            .event(added("Cordless drill", "18V, two batteries", 7))
            .`when`()
            .command(add(name = "Ladder", description = null, days = "3"))
            .then()
            .exceptionSatisfies { assertRefused("tool-id-in-use", it) }
    }
}
