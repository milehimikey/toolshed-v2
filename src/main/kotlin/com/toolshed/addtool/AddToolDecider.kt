package com.toolshed.addtool

import java.math.BigDecimal
import java.text.BreakIterator
import java.time.Instant

private const val INV_SIGNED_IN = "INV-ADD-1"
private const val INV_ID_IN_USE = "INV-ADD-10"
private const val INV_NAME_MISSING = "INV-ADD-4"
private const val INV_NAME_TOO_LONG = "INV-ADD-11"
private const val INV_DESCRIPTION_TOO_LONG = "INV-ADD-11"
private const val INV_PERIOD_MISSING = "INV-ADD-2"
private const val INV_PERIOD_OUT_OF_RANGE = "INV-ADD-3"

private const val MAX_NAME = 100
private const val MAX_DESCRIPTION = 500
private val PERIOD_RANGE = 1..30

/**
 * The Add Tool decision. Pure: no framework types. Identity is checked at the edge, before this runs.
 * Checks run in the doc's order; the first failure is the reason returned.
 */
object AddToolDecider {
    fun decide(
        command: AddTool,
        existing: ToolAdded?,
        now: Instant,
    ): List<ToolAdded> {
        val name = command.name?.trim().orEmpty()
        val description = command.description?.trim()?.takeIf { it.isNotEmpty() }

        // 2. The tool ID is already used (INV-ADD-10): same details succeeds with nothing recorded, else refused.
        if (existing != null) {
            return if (sameDetails(existing, name, description, command.defaultCheckoutPeriodDays)) {
                emptyList()
            } else {
                throw AddToolRejected(INV_ID_IN_USE, "tool-id-in-use", "That tool ID is already used by another tool.")
            }
        }

        // 3. name-missing, then name-too-long (INV-ADD-4, INV-ADD-11).
        if (name.isEmpty()) throw AddToolRejected(INV_NAME_MISSING, "name-missing", "A tool needs a name.")
        if (userVisibleLength(name) > MAX_NAME) {
            throw AddToolRejected(INV_NAME_TOO_LONG, "name-too-long", "A tool's name can be at most 100 characters.")
        }

        // 4. description-too-long (INV-ADD-11).
        if (description != null && userVisibleLength(description) > MAX_DESCRIPTION) {
            throw AddToolRejected(
                INV_DESCRIPTION_TOO_LONG,
                "description-too-long",
                "A tool's description can be at most 500 characters.",
            )
        }

        // 5. period-missing, then period-out-of-range (INV-ADD-2, INV-ADD-3).
        val period =
            command.defaultCheckoutPeriodDays
                ?: throw AddToolRejected(INV_PERIOD_MISSING, "period-missing", "A tool needs a default checkout period.")
        val days = wholeDays(period)
        if (days == null) {
            throw AddToolRejected(
                INV_PERIOD_OUT_OF_RANGE,
                "period-out-of-range",
                "The default checkout period must be a whole number of days from 1 to 30.",
            )
        }

        return listOf(ToolAdded(command.toolId, name, description, days, now))
    }
}

private fun sameDetails(
    existing: ToolAdded,
    name: String,
    description: String?,
    period: BigDecimal?,
): Boolean =
    existing.name == name &&
        existing.description == description &&
        period != null &&
        wholeDays(period) == existing.defaultCheckoutPeriodDays

/** A whole number of days in 1..30, or null when the value is fractional or out of range. */
private fun wholeDays(period: BigDecimal): Int? {
    val stripped = period.stripTrailingZeros()
    if (stripped.scale() > 0) return null
    // Range-check the full value before narrowing: a narrowing conversion would wrap large values into the range.
    if (stripped < BigDecimal(PERIOD_RANGE.first) || stripped > BigDecimal(PERIOD_RANGE.last)) return null
    return stripped.intValueExact()
}

/** Length in user-visible characters (grapheme clusters), not UTF-16 units or bytes. */
private fun userVisibleLength(text: String): Int {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    var count = 0
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        count++
        end = iterator.next()
    }
    return count
}
