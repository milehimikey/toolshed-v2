package com.toolshed.registerneighbor

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Register Neighbor's command handler. It loads the state through a criteria over the account-lifecycle
 * events and appends the decider's events on the processing context, so the append is checked against the
 * same events the decision read; two sign-ups for one email cannot both be recorded.
 */
@Component
class RegisterNeighborCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: RegisterNeighbor,
        @InjectEntity state: NeighborRegistryState,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                context.decideAndAppend { RegisterNeighborDecider.decide(command, state, clock.instant()) }
            } catch (refused: RegisterNeighborRejected) {
                logger.info { "register-neighbor neighborId=${command.neighborId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "register-neighbor neighborId=${command.neighborId.value} outcome=$outcome" }
    }
}
