package com.toolshed.addtool

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock
import java.util.UUID

/** Wires the Add Tool slice for the fixture: its entity and its command handler. Nothing else. */
object AddToolConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val entity = EventSourcedEntityModule.autodetected(UUID::class.java, ToolState::class.java)
        val commandHandling =
            CommandHandlingModule
                .named("AddTool")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { AddToolCommandHandler(clock) }
                .build()
        return configurer
            .registerEntity(entity)
            .registerCommandHandlingModule(commandHandling)
    }
}
