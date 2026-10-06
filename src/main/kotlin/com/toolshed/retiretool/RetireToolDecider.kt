package com.toolshed.retiretool

import com.toolshed.axon.CommandRejected
import java.time.Instant

/** A refused retirement. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class RetireToolRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/** The pure decision for Retire Tool: the command, the folded state, and the moment to record. */
object RetireToolDecider {
    /** Pure decision. The checks run in the doc's order; the first that fails is the reason returned. */
    fun decide(
        command: RetireTool,
        state: ToolState,
        retiredAt: Instant,
    ): List<ToolRetired> {
        if (!state.hasTool(command.toolId)) refuse("INV-RTR-10", "tool-not-found", "There is no such tool.")
        // INV-RTR-2: retiring a tool already retired (including one recorded lost) succeeds with nothing recorded.
        if (state.isRetired(command.toolId)) return emptyList()

        return listOf(ToolRetired(command.toolId, retiredAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw RetireToolRejected(invariant, reason, sentence)
}
