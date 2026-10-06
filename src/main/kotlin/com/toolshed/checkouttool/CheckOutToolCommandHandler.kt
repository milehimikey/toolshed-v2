package com.toolshed.checkouttool

import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

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
        eventAppender.append(CheckOutDecider.decide(command, state, today, now))
    }
}
