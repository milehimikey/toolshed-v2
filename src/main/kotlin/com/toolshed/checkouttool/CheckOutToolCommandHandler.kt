package com.toolshed.checkouttool

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/** Reads the clock once per command: the calendar date in the shed's zone, and the instant. */
@Component
class CheckOutToolCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: CheckOutTool,
        @InjectEntity state: CheckOutState,
        context: ProcessingContext,
    ) {
        val now = clock.instant()
        val today = now.atZone(clock.zone).toLocalDate()
        val events =
            try {
                context.decideAndAppend { CheckOutToolDecider.decide(command, state, today, now) }
            } catch (refused: CheckOutRefused) {
                logger.info { "check out refused loan=${command.loanId.value} tool=${command.toolId.value} reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (events.isEmpty()) "repeat, nothing recorded" else "recorded"
        logger.info { "check out $outcome loan=${command.loanId.value} tool=${command.toolId.value}" }
    }
}
