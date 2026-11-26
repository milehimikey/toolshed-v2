package com.toolshed.scheduletoolmaintenance

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Schedule Tool Maintenance's command handler. The shed manager's name is never logged (Amendment 12, PII baseline);
 * only the tool id and the outcome are.
 */
@Component
class ScheduleToolMaintenanceCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: ScheduleToolMaintenance,
        @InjectEntity state: ScheduleMaintenanceState,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                context.decideAndAppend { ScheduleToolMaintenanceDecider.decide(command, state, clock.instant()) }
            } catch (refused: ScheduleToolMaintenanceRejected) {
                logger.info { "schedule-tool-maintenance toolId=${command.toolId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome =
            when (recorded.firstOrNull()) {
                null -> "repeat"
                is ToolMaintenanceScheduled -> "scheduled"
                else -> "tasks-changed"
            }
        logger.info { "schedule-tool-maintenance toolId=${command.toolId.value} outcome=$outcome" }
    }
}
