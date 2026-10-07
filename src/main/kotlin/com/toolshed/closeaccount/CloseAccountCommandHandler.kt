package com.toolshed.closeaccount

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

@Component
class CloseAccountCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: CloseAccount,
        @InjectEntity state: NeighborAccountState,
        context: ProcessingContext,
    ) {
        val events =
            try {
                context.decideAndAppend { CloseAccountDecider.decide(command, state.facts, clock.instant()) }
            } catch (refused: CloseAccountRejected) {
                logger.info { "CloseAccount neighborId=${command.neighborId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        logger.info { "CloseAccount neighborId=${command.neighborId.value} outcome=${if (events.isEmpty()) "repeat" else "recorded"}" }
    }
}
