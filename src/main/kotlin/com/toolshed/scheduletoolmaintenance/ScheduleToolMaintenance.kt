package com.toolshed.scheduletoolmaintenance

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.NOT_SIGNED_IN
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import org.axonframework.modelling.annotation.TargetEntityId

/**
 * The shed manager sending a tool for maintenance, or editing the work of a tool already in maintenance.
 * [managerName] is the signed-in shed manager's name, set from sign-in by the controller (Amendment 12); it is never
 * a field the shed manager types, and the handler records it as scheduledBy or changedBy. [tasks] and [notes] are
 * raw: the decider tidies them and applies the limits.
 */
data class ScheduleToolMaintenance(
    val toolId: ToolId,
    val tasks: List<String>?,
    val notes: String?,
    val managerName: String?,
) {
    @TargetEntityId
    private fun ledger(): MaintenanceLedger = MaintenanceLedger
}

/** The consistency boundary for scheduling: one id for the shed's tool, loan, hold and maintenance events. */
object MaintenanceLedger

private val NOT_SIGNED_IN_REFUSAL = Refusal("INV-STM-1", "Please sign in.")

/**
 * Identity checks that run before the command is sent (Amendments 6 and 12): not signed in (no identity at all, or the
 * role without a name, INV-STM-10), then the shed manager's role.
 */
fun Caller.requireMaintenanceScheduler(managerName: String?) {
    if (isShedManager && managerName.isNullOrBlank()) {
        throw CommandRejected("INV-STM-10", NOT_SIGNED_IN, NOT_SIGNED_IN_REFUSAL.sentence)
    }
    requireShedManager(
        notSignedIn = NOT_SIGNED_IN_REFUSAL,
        notShedManager = Refusal("INV-STM-1", "Only the shed manager can schedule tool maintenance."),
    )
}
