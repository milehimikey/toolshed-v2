package com.toolshed.flagneighbor

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

@Component
class FlagNeighborCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: FlagNeighbor,
        @InjectEntity state: FlagState,
        context: ProcessingContext,
    ) {
        // One clock reading: today is that moment's date in the shed's zone, and flaggedAt is that same moment.
        val now = clock.instant()
        val today = now.atZone(clock.zone).toLocalDate()
        val events =
            try {
                context.decideAndAppend { FlagNeighborDecider.decide(command, state.facts, now, today) }
            } catch (refused: FlagNeighborRejected) {
                logger.info { "FlagNeighbor neighborId=${command.neighborId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        logger.info { "FlagNeighbor neighborId=${command.neighborId.value} outcome=${if (events.isEmpty()) "repeat" else "recorded"}" }
    }
}
