package com.toolshed.reinstateneighbor

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

@Component
class ReinstateNeighborCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: ReinstateNeighbor,
        @InjectEntity state: NeighborState,
        context: ProcessingContext,
    ) {
        val events =
            try {
                context.decideAndAppend { ReinstateNeighborDecider.decide(command, state.facts, clock.instant()) }
            } catch (refused: CommandRejected) {
                logger.info { "ReinstateNeighbor neighborId=${command.neighborId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        logger.info {
            "ReinstateNeighbor neighborId=${command.neighborId.value} outcome=${if (events.isEmpty()) "already-active" else "recorded"}"
        }
    }
}
