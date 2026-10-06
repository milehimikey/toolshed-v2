package com.toolshed.returntool

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations.
 * In the running app the handler is a Spring bean and the State entity is picked up from its
 * @EventSourced stereotype.
 */
object ReturnToolConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val stateEntity =
            EventSourcedEntityModule.autodetected(
                LoanBook::class.java,
                ReturnToolCommandHandler.State::class.java,
            )
        val commandHandlingModule =
            CommandHandlingModule
                .named("ReturnTool")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { ReturnToolCommandHandler(clock) }

        return configurer
            .registerEntity(stateEntity)
            .registerCommandHandlingModule(commandHandlingModule)
    }
}
