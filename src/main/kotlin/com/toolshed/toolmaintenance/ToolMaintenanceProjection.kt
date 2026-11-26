package com.toolshed.toolmaintenance

import com.toolshed.completetoolmaintenance.ToolMaintenanceCompleted
import com.toolshed.retiretool.ToolRetired
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceScheduled
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceTasksChanged
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Applies exactly the four events the Tool Maintenance view reads (INV-TMNT-5). Names and notes are personal or
 * free text, so only ids are logged.
 */
@Component
class ToolMaintenanceProjection(
    private val repository: ToolMaintenanceRepository,
) {
    @EventHandler
    fun on(event: ToolMaintenanceScheduled) {
        logger.debug { "tool-maintenance scheduled toolId=${event.toolId.value}" }
        repository.schedule(
            ToolMaintenanceRow(
                toolId = event.toolId,
                toolName = event.toolName,
                scheduledBy = event.scheduledBy,
                scheduledAt = event.scheduledAt,
                tasks = event.tasks,
                notes = event.notes,
                lastChangedBy = null,
                lastChangedAt = null,
            ),
        )
    }

    @EventHandler
    fun on(event: ToolMaintenanceTasksChanged) {
        logger.debug { "tool-maintenance tasks-changed toolId=${event.toolId.value}" }
        repository.changeTasks(event.toolId, event.tasks, event.notes, event.changedBy, event.changedAt)
    }

    @EventHandler
    fun on(event: ToolMaintenanceCompleted) {
        logger.debug { "tool-maintenance completed toolId=${event.toolId.value}" }
        repository.complete(event.toolId, event.completedAt)
    }

    @EventHandler
    fun on(event: ToolRetired) {
        logger.debug { "tool-maintenance tool-retired toolId=${event.toolId.value}" }
        repository.retire(event.toolId)
    }
}
