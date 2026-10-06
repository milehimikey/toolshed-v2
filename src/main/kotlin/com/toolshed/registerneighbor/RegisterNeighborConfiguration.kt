package com.toolshed.registerneighbor

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations.
 * In the running app the handler is a Spring bean and is picked up by annotation scanning.
 */
object RegisterNeighborConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val stateEntity =
            EventSourcedEntityModule.autodetected(
                NeighborRegistry::class.java,
                NeighborRegistryState::class.java,
            )
        val commandHandlingModule =
            CommandHandlingModule
                .named("RegisterNeighbor")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { RegisterNeighborCommandHandler(clock) }

        return configurer
            .registerEntity(stateEntity)
            .registerCommandHandlingModule(commandHandlingModule)
    }
}
