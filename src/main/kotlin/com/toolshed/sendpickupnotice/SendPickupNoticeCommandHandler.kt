package com.toolshed.sendpickupnotice

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/** One clock reading serves the pickup-window check and sentAt; then decide and append through the foundation runner. */
@Component
class SendPickupNoticeCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: SendPickupNotice,
        @InjectEntity state: SendPickupNoticeState,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                context.decideAndAppend { SendPickupNoticeDecider.decide(command, state, clock.instant(), clock.zone) }
            } catch (refused: SendPickupNoticeRejected) {
                logger.info { "send-pickup-notice holdId=${command.holdId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "send-pickup-notice holdId=${command.holdId.value} outcome=$outcome" }
    }
}
