package com.toolshed.placehold

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Place Hold's command handler. It loads the state through one criteria over every upstream event type and
 * appends the decider's events on the processing context, so holds on a tool are decided one at a time.
 */
@Component
class PlaceHoldCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: PlaceHold,
        @InjectEntity state: HoldState,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                context.decideAndAppend { PlaceHoldDecider.decide(command, state, clock.instant()) }
            } catch (refused: PlaceHoldRejected) {
                logger.info { "place-hold holdId=${command.holdId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "place-hold holdId=${command.holdId.value} outcome=$outcome" }
    }
}
