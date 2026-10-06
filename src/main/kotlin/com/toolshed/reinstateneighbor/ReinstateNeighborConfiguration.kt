package com.toolshed.reinstateneighbor

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock
import java.util.UUID

/** Wires the Reinstate Neighbor slice for the fixture: its entity and its command handler. Nothing else. */
object ReinstateNeighborConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val entity = EventSourcedEntityModule.autodetected(UUID::class.java, NeighborState::class.java)
        val commandHandling =
            CommandHandlingModule
                .named("ReinstateNeighbor")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { ReinstateNeighborCommandHandler(clock) }
                .build()
        return configurer
            .registerEntity(entity)
            .registerCommandHandlingModule(commandHandling)
    }
}
