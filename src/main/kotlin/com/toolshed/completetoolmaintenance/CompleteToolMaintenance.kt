package com.toolshed.completetoolmaintenance

import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import org.axonframework.modelling.annotation.TargetEntityId

/** The shed manager putting a tool back in service once its maintenance is done. Carries no tasks, notes or date. */
data class CompleteToolMaintenance(
    val toolId: ToolId,
) {
    @TargetEntityId
    private fun ledger(): MaintenanceLedger = MaintenanceLedger
}

/** The consistency boundary for maintenance completions: one id for the shed's tools and their maintenance events. */
object MaintenanceLedger

/** Identity checks that run before the command is sent (Amendment 6): signed in, and the shed manager's role. */
fun Caller.requireMaintenanceCompleter() {
    requireShedManager(
        notSignedIn = Refusal("INV-CTMN-1", "Please sign in."),
        notShedManager = Refusal("INV-CTMN-1", "Only the shed manager can complete tool maintenance."),
    )
}
