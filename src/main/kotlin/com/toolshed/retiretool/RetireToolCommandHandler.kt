package com.toolshed.retiretool

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Retire Tool's command handler. It loads the state through one criteria over the tool events and appends the
 * decider's events on the processing context, so a retirement and its append are one boundary.
 */
@Component
class RetireToolCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: RetireTool,
        @InjectEntity state: ToolState,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                context.decideAndAppend { RetireToolDecider.decide(command, state, clock.instant()) }
            } catch (refused: RetireToolRejected) {
                logger.info { "retire-tool toolId=${command.toolId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "retire-tool toolId=${command.toolId.value} outcome=$outcome" }
    }
}
