package com.toolshed.flagneighbor

import com.toolshed.axon.ReactionOutcome
import com.toolshed.neighborstoflag.NeighborsToFlag
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import java.time.Clock
import java.util.UUID

/** Wires the Flag Neighbor slice for the fixture: its entity and its command handler. Nothing else. */
object FlagNeighborConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val entity = EventSourcedEntityModule.autodetected(UUID::class.java, FlagState::class.java)
        val commandHandling =
            CommandHandlingModule
                .named("FlagNeighbor")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { FlagNeighborCommandHandler(clock) }
                .build()
        return configurer
            .registerEntity(entity)
            .registerCommandHandlingModule(commandHandling)
    }

    /** Wires the Late Return Flagger alone, with its list and its dispatch supplied, so a test can observe the reaction. */
    fun configureFlagger(
        configurer: EventSourcingConfigurer,
        readList: () -> NeighborsToFlag,
        send: (Any) -> ReactionOutcome,
    ): EventSourcingConfigurer {
        val processor =
            EventProcessorModule
                .pooledStreaming("LateReturnFlaggerHandler")
                .eventHandlingComponents { it.autodetected { LateReturnFlaggerHandler(readList, send) } }
                .notCustomized()
        return configurer.modelling { m ->
            m.messaging { ms -> ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(processor) } } }
        }
    }
}
