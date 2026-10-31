package com.toolshed.fulfillhold

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/** Fulfill Hold's command handler: one clock reading serves both the collect-by date and fulfilledAt. */
@Component
class FulfillHoldCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: FulfillHold,
        @InjectEntity state: FulfillState,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                context.decideAndAppend { FulfillHoldDecider.decide(command, state, clock.instant(), clock.zone) }
            } catch (refused: FulfillHoldRejected) {
                logger.info { "fulfill-hold holdId=${command.holdId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "fulfill-hold holdId=${command.holdId.value} outcome=$outcome" }
    }
}
