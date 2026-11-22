package com.toolshed.toolmaintenance

import com.toolshed.axon.ToolId
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * The in-memory read model behind the Tool Maintenance view (Amendment 5). A tool is in the map or it is not:
 * no status, no history (INV-TMNT-4). Every apply is idempotent (INV-TMNT-6).
 */
@Component
class ToolMaintenanceRepository {
    private val entries = ConcurrentHashMap<ToolId, ToolMaintenanceRow>()

    /** Creates the entry; a tool that already has one keeps it as it was (INV-TMNT-13). */
    fun schedule(row: ToolMaintenanceRow) {
        entries.putIfAbsent(row.toolId, row)
    }

    /** Replaces tasks and notes as a whole (INV-TMNT-11); a tool with no entry changes nothing (INV-TMNT-12). */
    fun changeTasks(
        toolId: ToolId,
        tasks: List<String>,
        notes: String?,
        changedBy: String,
        changedAt: Instant,
    ) {
        entries.computeIfPresent(toolId) { _, row ->
            row.copy(tasks = tasks, notes = notes, lastChangedBy = changedBy, lastChangedAt = changedAt)
        }
    }

    /** Removes the entry; a tool with none changes nothing (INV-TMNT-2, INV-TMNT-3). */
    fun remove(toolId: ToolId) {
        entries.remove(toolId)
    }

    /** The screen's rows in INV-TMNT-8 order. */
    fun rows(): List<ToolMaintenanceRow> =
        entries.values.sortedWith(
            compareBy({ it.toolName.lowercase() }, { it.scheduledAt }, { it.toolId.value.toString() }),
        )
}
