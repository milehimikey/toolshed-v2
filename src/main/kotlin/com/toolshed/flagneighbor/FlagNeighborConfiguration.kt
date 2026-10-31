package com.toolshed.flagneighbor

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
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
}
