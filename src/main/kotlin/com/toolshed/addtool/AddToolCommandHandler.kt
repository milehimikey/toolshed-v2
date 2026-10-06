package com.toolshed.addtool

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

@Component
class AddToolCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: AddTool,
        @InjectEntity state: ToolState,
        context: ProcessingContext,
    ) {
        val events =
            try {
                context.decideAndAppend { AddToolDecider.decide(command, state.added, clock.instant()) }
            } catch (refused: AddToolRejected) {
                logger.info { "AddTool toolId=${command.toolId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        logger.info { "AddTool toolId=${command.toolId.value} outcome=${if (events.isEmpty()) "repeat" else "recorded"}" }
    }
}
