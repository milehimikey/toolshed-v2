package com.toolshed.suspendneighbor

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock

/** Wires the Suspend Neighbor slice for the fixture: its entity and its command handler. Nothing else. */
object SuspendNeighborConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val entity = EventSourcedEntityModule.autodetected(NeighborStatusId::class.java, NeighborStatusState::class.java)
        val commandHandling =
            CommandHandlingModule
                .named("SuspendNeighbor")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { SuspendNeighborCommandHandler(clock) }
                .build()
        return configurer
            .registerEntity(entity)
            .registerCommandHandlingModule(commandHandling)
    }
}
