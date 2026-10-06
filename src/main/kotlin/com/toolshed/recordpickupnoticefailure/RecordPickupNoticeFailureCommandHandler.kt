package com.toolshed.recordpickupnoticefailure

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/** Stamps the failure with the clock reading, then decides and appends through the foundation runner. */
@Component
class RecordPickupNoticeFailureCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: RecordPickupNoticeFailure,
        @InjectEntity state: PickupNoticeState,
        context: ProcessingContext,
    ) {
        val failedAt = clock.instant()
        val events =
            try {
                context.decideAndAppend { RecordPickupNoticeFailureDecider.decide(command, state, failedAt) }
            } catch (refused: RecordPickupNoticeFailureRefused) {
                logger.info { "pickup-notice-failure hold=${command.holdId.value} reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (events.isEmpty()) "repeat, nothing recorded" else "recorded"
        logger.info { "pickup-notice-failure hold=${command.holdId.value} outcome=$outcome" }
    }
}
