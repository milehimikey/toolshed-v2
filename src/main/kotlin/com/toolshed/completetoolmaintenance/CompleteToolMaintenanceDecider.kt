package com.toolshed.completetoolmaintenance

import com.toolshed.axon.CommandRejected
import java.time.Instant

/** A refused completion. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class CompleteToolMaintenanceRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/** The pure decision for Complete Tool Maintenance: the command, the folded state, and the moment to record. */
object CompleteToolMaintenanceDecider {
    /** Pure decision. The checks run in the doc's order; the first that fails is the reason returned. */
    fun decide(
        command: CompleteToolMaintenance,
        state: CompleteMaintenanceState,
        completedAt: Instant,
    ): List<ToolMaintenanceCompleted> {
        if (!state.hasTool(command.toolId)) refuse("INV-CTMN-3", "tool-not-found", "There is no such tool.")
        if (state.isRetired(command.toolId)) refuse("INV-CTMN-4", "tool-retired", "That tool has been retired.")
        // INV-CTMN-2: a tool not in maintenance (already completed, or never sent) succeeds with nothing recorded.
        if (!state.isInMaintenance(command.toolId)) return emptyList()

        return listOf(ToolMaintenanceCompleted(command.toolId, completedAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw CompleteToolMaintenanceRejected(invariant, reason, sentence)
}
