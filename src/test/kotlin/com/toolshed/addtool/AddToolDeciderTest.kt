package com.toolshed.addtool

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.ToolId
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AddToolDeciderTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val t1 = ToolId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val t2 = ToolId(UUID.fromString("00000000-0000-0000-0000-000000000002"))

    private fun add(
        name: String? = "Cordless drill",
        description: String? = "18V, two batteries",
        days: String? = "7",
        tool: ToolId = t1,
    ) = AddTool(tool, name, description, days?.let { BigDecimal(it) })

    private fun added(
        name: String,
        description: String?,
        days: Int,
        tool: ToolId = t1,
    ) = ToolAdded(tool, name, description, days, now)

    private fun refusal(block: () -> Unit): CommandRejected = assertFailsWith<CommandRejected>(block = block)

    private fun assertRefused(
        reason: String,
        invariant: String,
        command: AddTool,
        existing: ToolAdded? = null,
    ) {
        val rejected = refusal { AddToolDecider.decide(command, existing, now) }
        assertEquals(reason, rejected.reason)
        assertEquals(invariant, rejected.invariant)
    }

    // --- happy paths ---

    @Test
    fun `INV-ADD-2 - given the shed has no tools, when add cordless drill with description and 7 days, then Tool Added recorded`() {
        assertEquals(listOf(added("Cordless drill", "18V, two batteries", 7)), AddToolDecider.decide(add(), null, now))
    }

    @Test
    fun `INV-ADD-4 - given no tools, when add ladder with 3 days and no description, then recorded with no description`() {
        assertEquals(listOf(added("Ladder", null, 3)), AddToolDecider.decide(add("Ladder", null, "3"), null, now))
    }

    @Test
    fun `INV-ADD-4 - given no tools, when description is only whitespace, then recorded with no description`() {
        assertEquals(listOf(added("Ladder", null, 3)), AddToolDecider.decide(add("Ladder", "   ", "3"), null, now))
    }

    @Test
    fun `INV-ADD-4 - given no tools, when name has surrounding whitespace, then trimmed`() {
        assertEquals(listOf(added("Ladder", null, 3)), AddToolDecider.decide(add("  Ladder  ", null, "3"), null, now))
    }

    @Test
    fun `INV-ADD-5 INV-ADD-8 INV-ADD-9 - given T1 is a cordless drill, when a second identical drill T2 is added, then separate tool`() {
        val result = AddToolDecider.decide(add(tool = t2), null, now)
        assertEquals(listOf(added("Cordless drill", "18V, two batteries", 7, t2)), result)
    }

    @Test
    fun `INV-ADD-11 - given no tools, when name is 100 and description is 500 characters, then recorded`() {
        val name = "n".repeat(100)
        val description = "d".repeat(500)
        assertEquals(listOf(added(name, description, 7)), AddToolDecider.decide(add(name, description, "7"), null, now))
    }

    @Test
    fun `INV-ADD-11 - name and description are counted in user-visible characters, not UTF-16 units`() {
        // Each "é" written as e + combining accent is one user-visible character but two code points.
        val name = "é".repeat(100)
        assertEquals(listOf(added(name, null, 7)), AddToolDecider.decide(add(name, null, "7"), null, now))
    }

    @Test
    fun `INV-ADD-3 - given no tools, when period is 1 day, then recorded`() {
        assertEquals(listOf(added("Stud finder", null, 1)), AddToolDecider.decide(add("Stud finder", null, "1"), null, now))
    }

    @Test
    fun `INV-ADD-3 - given no tools, when period is 30 days, then recorded`() {
        assertEquals(listOf(added("Tile saw", null, 30)), AddToolDecider.decide(add("Tile saw", null, "30"), null, now))
    }

    // --- refusals, in the doc's check order ---

    @Test
    fun `INV-ADD-4 - given no tools, when name is empty, then refused name-missing`() {
        assertRefused("name-missing", "INV-ADD-4", add(name = ""))
    }

    @Test
    fun `INV-ADD-4 - given no tools, when name is only whitespace, then refused name-missing`() {
        assertRefused("name-missing", "INV-ADD-4", add(name = "   "))
    }

    @Test
    fun `INV-ADD-4 - given no tools, when name is absent, then refused name-missing`() {
        assertRefused("name-missing", "INV-ADD-4", add(name = null))
    }

    @Test
    fun `INV-ADD-11 - given no tools, when name is 101 characters, then refused name-too-long with the doc sentence`() {
        val rejected = refusal { AddToolDecider.decide(add(name = "n".repeat(101)), null, now) }
        assertEquals("name-too-long", rejected.reason)
        assertEquals("INV-ADD-11", rejected.invariant)
        assertEquals("A tool's name can be at most 100 characters.", rejected.message)
    }

    @Test
    fun `INV-ADD-11 - given no tools, when description is 501 characters, then refused description-too-long`() {
        val rejected = refusal { AddToolDecider.decide(add(description = "d".repeat(501)), null, now) }
        assertEquals("description-too-long", rejected.reason)
        assertEquals("INV-ADD-11", rejected.invariant)
        assertEquals("A tool's description can be at most 500 characters.", rejected.message)
    }

    @Test
    fun `INV-ADD-2 - given no tools, when no default period is given, then refused period-missing`() {
        val rejected = refusal { AddToolDecider.decide(add(days = null), null, now) }
        assertEquals("period-missing", rejected.reason)
        assertEquals("INV-ADD-2", rejected.invariant)
        assertEquals("A tool needs a default checkout period.", rejected.message)
    }

    @Test
    fun `INV-ADD-3 - given no tools, when period is 31 days, then refused period-out-of-range`() {
        assertRefused("period-out-of-range", "INV-ADD-3", add(days = "31"))
    }

    @Test
    fun `INV-ADD-3 - given no tools, when period is 0 days, then refused period-out-of-range`() {
        assertRefused("period-out-of-range", "INV-ADD-3", add(days = "0"))
    }

    @Test
    fun `INV-ADD-3 - given no tools, when period is two and a half days, then refused period-out-of-range`() {
        val rejected = refusal { AddToolDecider.decide(add(days = "2.5"), null, now) }
        assertEquals("period-out-of-range", rejected.reason)
        assertEquals("The default checkout period must be a whole number of days from 1 to 30.", rejected.message)
    }

    @Test
    fun `INV-ADD-3 - a period written with trailing zeros is still whole days`() {
        assertEquals(listOf(added("Ladder", null, 7)), AddToolDecider.decide(add("Ladder", null, "7.00"), null, now))
    }

    @Test
    fun `INV-ADD-10 - given T1 exists, when the same request arrives again, then succeeds and records nothing`() {
        assertEquals(emptyList(), AddToolDecider.decide(add(), added("Cordless drill", "18V, two batteries", 7), now))
    }

    @Test
    fun `INV-ADD-10 - given T1 exists and was retired since, when the same request arrives again, then nothing recorded`() {
        // Retirement is another slice's event; the entity keeps the Tool Added fact, so the same repeat still succeeds.
        assertEquals(emptyList(), AddToolDecider.decide(add(), added("Cordless drill", "18V, two batteries", 7), now))
    }

    @Test
    fun `INV-ADD-10 - given T1 is Ladder 3 days, when a repeat differs only in whitespace, then nothing recorded`() {
        assertEquals(emptyList(), AddToolDecider.decide(add("  Ladder ", null, "3"), added("Ladder", null, 3), now))
    }

    @Test
    fun `INV-ADD-10 - given T1 is cordless drill 7 days, when T1 is reused for a ladder, then refused tool-id-in-use`() {
        val rejected =
            refusal {
                AddToolDecider.decide(add("Ladder", null, "3"), added("Cordless drill", "18V, two batteries", 7), now)
            }
        assertEquals("tool-id-in-use", rejected.reason)
        assertEquals("INV-ADD-10", rejected.invariant)
        assertEquals("That tool ID is already used by another tool.", rejected.message)
    }

    @Test
    fun `INV-ADD-10 - a repeat with an absent period is a different request, so it is refused tool-id-in-use`() {
        assertRefused("tool-id-in-use", "INV-ADD-10", add(days = null), added("Cordless drill", "18V, two batteries", 7))
    }

    @Test
    fun `INV-ADD-10 - the id check runs before the name checks`() {
        // A repeat with a too-long name is refused tool-id-in-use, not name-too-long, because step 2 comes first.
        assertRefused("tool-id-in-use", "INV-ADD-10", add(name = "n".repeat(101)), added("Cordless drill", "18V, two batteries", 7))
    }

    @Test
    fun `refusals record nothing and never return a partial list`() {
        assertTrue(runCatching { AddToolDecider.decide(add(name = ""), null, now) }.isFailure)
    }

    @Test
    fun `INV-ADD-3 - given a period of 4294967303 days, when added, then refused period-out-of-range, not wrapped to 7`() {
        assertRefused("period-out-of-range", "INV-ADD-3", add(days = "4294967303"))
    }

    @Test
    fun `INV-ADD-3 - given a huge negative period, when added, then refused period-out-of-range`() {
        assertRefused("period-out-of-range", "INV-ADD-3", add(days = "-4294967295"))
    }

    @Test
    fun `INV-ADD-11 - given a name of 101 user-visible characters with combining accents, then refused name-too-long`() {
        assertRefused("name-too-long", "INV-ADD-11", add(name = "e\u0301".repeat(101)))
    }

    @Test
    fun `INV-ADD-11 - given a description of exactly 500 characters, then recorded`() {
        assertEquals(listOf(added("Ladder", "d".repeat(500), 3)), AddToolDecider.decide(add("Ladder", "d".repeat(500), "3"), null, now))
    }

    @Test
    fun `INV-ADD-11 - given a description of 501 characters, then refused description-too-long`() {
        assertRefused("description-too-long", "INV-ADD-11", add("Ladder", "d".repeat(501), "3"))
    }

    @Test
    fun `INV-ADD-4 - given a name wrapped in no-break spaces and ideographic spaces, then trimmed`() {
        assertEquals(listOf(added("Ladder", null, 3)), AddToolDecider.decide(add("\u00A0\u3000Ladder\u2003", null, "3"), null, now))
    }

    @Test
    fun `INV-ADD-4 - given a name of only an em space, then refused name-missing`() {
        assertRefused("name-missing", "INV-ADD-4", add(name = "\u2003"))
    }

    @Test
    fun `INV-ADD-4 - given a description of only an ideographic space, then recorded with no description`() {
        assertEquals(listOf(added("Ladder", null, 3)), AddToolDecider.decide(add("Ladder", "\u3000", "3"), null, now))
    }
}
