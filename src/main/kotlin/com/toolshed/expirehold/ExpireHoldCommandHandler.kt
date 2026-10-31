package com.toolshed.expirehold

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/** Expire Hold's command handler: one clock reading serves both "today" and expiredAt. */
@Component
class ExpireHoldCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: ExpireHold,
        @InjectEntity state: ExpireState,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                context.decideAndAppend { ExpireHoldDecider.decide(command, state, clock.instant(), clock.zone) }
            } catch (refused: ExpireHoldRejected) {
                logger.info { "expire-hold holdId=${command.holdId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "expire-hold holdId=${command.holdId.value} outcome=$outcome" }
    }
}
