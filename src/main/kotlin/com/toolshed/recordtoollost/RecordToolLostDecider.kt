package com.toolshed.recordtoollost

import com.toolshed.axon.CommandRejected
import java.time.Instant

/** A refused loss record. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class RecordToolLostRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/**
 * The loss decision, pure. Checks run in the doc's order; a loan already recorded lost succeeds with nothing recorded.
 * Identity (not-signed-in, not-shed-manager) is checked by the controller before this runs.
 */
object RecordToolLostDecider {
    fun decide(
        command: RecordToolLost,
        state: LoanState,
        lostAt: Instant,
    ): List<ToolLost> {
        // A toolId that isn't this loan's tool names no loan at that URI (lead ruling, record-tool-lost route).
        val tool =
            state.toolId()?.takeIf { it == command.toolId }
                ?: refuse("INV-LOST-11", "loan-not-found", "There is no such loan.")
        if (state.lost()) return emptyList()
        if (state.returned()) refuse("INV-LOST-2", "loan-ended", "This loan has ended.")
        return listOf(ToolLost(command.loanId, tool, lostAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw RecordToolLostRejected(invariant, reason, sentence)
}
