package com.toolshed.checkouttool

import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.eventhandling.gateway.EventAppender
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
        eventAppender: EventAppender,
    ) {
        val now = clock.instant()
        val today = now.atZone(clock.zone).toLocalDate()
        val events =
            try {
                CheckOutDecider.decide(command, state, today, now)
            } catch (refused: CheckOutRefused) {
                logger.info { "check out refused loan=${command.loanId.value} tool=${command.toolId.value} reason=${refused.reason}" }
                throw refused
            }
        eventAppender.append(events)
        val outcome = if (events.isEmpty()) "repeat, nothing recorded" else "recorded"
        logger.info { "check out $outcome loan=${command.loanId.value} tool=${command.toolId.value}" }
    }
}
