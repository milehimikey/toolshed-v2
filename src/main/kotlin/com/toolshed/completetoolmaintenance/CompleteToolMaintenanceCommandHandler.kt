package com.toolshed.completetoolmaintenance

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/** Complete Tool Maintenance's command handler: loads the state, decides, and appends on the processing context. */
@Component
class CompleteToolMaintenanceCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: CompleteToolMaintenance,
        @InjectEntity state: CompleteMaintenanceState,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                context.decideAndAppend { CompleteToolMaintenanceDecider.decide(command, state, clock.instant()) }
            } catch (refused: CompleteToolMaintenanceRejected) {
                logger.info { "complete-tool-maintenance toolId=${command.toolId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "complete-tool-maintenance toolId=${command.toolId.value} outcome=$outcome" }
    }
}
