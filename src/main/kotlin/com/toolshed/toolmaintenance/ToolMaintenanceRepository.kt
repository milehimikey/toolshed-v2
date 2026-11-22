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

    private val dropped = ConcurrentHashMap.newKeySet<ToolId>()
    private val lastMaintenanceSeen = ConcurrentHashMap<ToolId, Instant>()

    /**
     * Creates the entry; a tool that already has one keeps it as it was (INV-TMNT-13). A tool retired for good never
     * comes back, and an event whose scheduledAt is not later than the latest maintenance instant seen for the tool is
     * a replay and is ignored (INV-TMNT-6); a genuinely later one makes a fresh entry (INV-TMNT-4).
     */
    fun schedule(row: ToolMaintenanceRow) {
        if (row.toolId in dropped) return
        lastMaintenanceSeen.compute(row.toolId) { _, seen ->
            if (seen != null && !row.scheduledAt.isAfter(seen)) {
                seen
            } else {
                entries.putIfAbsent(row.toolId, row)
                row.scheduledAt
            }
        }
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
            // A change recorded before this entry's maintenance was scheduled belongs to an earlier maintenance:
            // a replay, left out (INV-TMNT-6, INV-TMNT-4).
            if (changedAt.isBefore(row.scheduledAt)) return@computeIfPresent row
            row.copy(tasks = tasks, notes = notes, lastChangedBy = changedBy, lastChangedAt = changedAt)
        }
    }

    /**
     * Removes the entry when maintenance is completed (INV-TMNT-2); a tool with none changes nothing. A completion
     * earlier than the latest maintenance instant seen is a replay and is ignored (INV-TMNT-6).
     */
    fun complete(
        toolId: ToolId,
        completedAt: Instant,
    ) {
        lastMaintenanceSeen.compute(toolId) { _, seen ->
            if (seen != null && completedAt.isBefore(seen)) {
                seen
            } else {
                entries.remove(toolId)
                completedAt
            }
        }
    }

    /** Removes the entry for good: a retired tool never shows as needing attention again (INV-TMNT-3, INV-TMNT-6). */
    fun retire(toolId: ToolId) {
        dropped.add(toolId)
        entries.remove(toolId)
    }

    /** The screen's rows in INV-TMNT-8 order. */
    fun rows(): List<ToolMaintenanceRow> =
        entries.values.sortedWith(
            compareBy({ it.toolName.lowercase() }, { it.scheduledAt }, { it.toolId.value.toString() }),
        )
}
