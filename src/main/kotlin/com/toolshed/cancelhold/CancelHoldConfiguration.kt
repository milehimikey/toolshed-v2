package com.toolshed.cancelhold

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations.
 * In the running app the handler is a Spring bean and the State entity is picked up from its
 * @EventSourced stereotype.
 */
object CancelHoldConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val stateEntity =
            EventSourcedEntityModule.autodetected(
                HoldLedger::class.java,
                CancelHoldCommandHandler.State::class.java,
            )
        val commandHandlingModule =
            CommandHandlingModule
                .named("CancelHold")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { CancelHoldCommandHandler(clock) }

        return configurer
            .registerEntity(stateEntity)
            .registerCommandHandlingModule(commandHandlingModule)
    }
}
