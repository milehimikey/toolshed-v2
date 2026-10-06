package com.toolshed.reinstateneighbor

import com.toolshed.axon.decideAndAppend
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

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
        context.decideAndAppend { ReinstateNeighborDecider.decide(command, state.facts, clock.instant()) }
    }
}
