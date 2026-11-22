package com.toolshed.toolmaintenance

import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import java.time.Instant

/** One row of the Tool Maintenance view. Absent fields stay null: notes, and lastChangedBy/lastChangedAt until a change. */
data class ToolMaintenanceRow(
    val toolId: ToolId,
    val toolName: String,
    val scheduledBy: String,
    val scheduledAt: Instant,
    val tasks: List<String>,
    val notes: String?,
    val lastChangedBy: String?,
    val lastChangedAt: Instant?,
)

/** The query's answer: every tool in maintenance, in INV-TMNT-8 order. No paging; empty is not a refusal. */
data class ToolMaintenance(
    val tools: List<ToolMaintenanceRow>,
)

/** The query the Tool Maintenance screen asks. Takes nothing. */
object ListToolsInMaintenance

/** Identity checks that run before the query is asked (Amendment 6): INV-TMNT-9, then INV-TMNT-10. */
fun Caller.requireToolMaintenanceReader() {
    requireShedManager(
        notSignedIn = Refusal("INV-TMNT-9", "Please sign in."),
        notShedManager = Refusal("INV-TMNT-10", "Only the shed manager can see the tools in maintenance."),
    )
}
