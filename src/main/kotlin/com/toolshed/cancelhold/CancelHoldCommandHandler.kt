package com.toolshed.cancelhold

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Cancel Hold's command handler. It loads the state through one criteria over the hold lifecycle events and
 * appends the decider's events on the processing context, so the decision and its append are one boundary.
 */
@Component
class CancelHoldCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: CancelHold,
        @InjectEntity state: CancelHoldState,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                context.decideAndAppend { CancelHoldDecider.decide(command, state, clock.instant()) }
            } catch (refused: CancelHoldRejected) {
                logger.info { "cancel-hold holdId=${command.holdId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "cancel-hold holdId=${command.holdId.value} outcome=$outcome" }
    }
}
