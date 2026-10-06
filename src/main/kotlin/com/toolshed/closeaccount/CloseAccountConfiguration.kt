package com.toolshed.closeaccount

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock
import java.util.UUID

/** Wires the Close Account slice for the fixture: its entity and its command handler. Nothing else. */
object CloseAccountConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val entity = EventSourcedEntityModule.autodetected(UUID::class.java, NeighborAccountState::class.java)
        val commandHandling =
            CommandHandlingModule
                .named("CloseAccount")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { CloseAccountCommandHandler(clock) }
                .build()
        return configurer
            .registerEntity(entity)
            .registerCommandHandlingModule(commandHandling)
    }
}
