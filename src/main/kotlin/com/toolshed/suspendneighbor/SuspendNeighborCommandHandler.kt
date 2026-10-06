package com.toolshed.suspendneighbor

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/** Stamps the suspension with the clock reading, then decides and appends through the foundation runner. */
@Component
class SuspendNeighborCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: SuspendNeighbor,
        @InjectEntity state: NeighborStatusState,
        context: ProcessingContext,
    ) {
        val now = clock.instant()
        val events =
            try {
                context.decideAndAppend { SuspendNeighborDecider.decide(command, state, now) }
            } catch (refused: SuspendNeighborRejected) {
                logger.info { "suspend refused neighbor=${command.neighborId.value} reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (events.isEmpty()) "repeat, nothing recorded" else "recorded"
        logger.info { "suspend $outcome neighbor=${command.neighborId.value}" }
    }
}
