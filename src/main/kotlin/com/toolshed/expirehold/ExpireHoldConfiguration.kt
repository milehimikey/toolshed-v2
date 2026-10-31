package com.toolshed.expirehold

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations: the command side. The Pickup
 * Expirer is schedule-driven (Amendment 7/11 exception) and has no processor. In the running app the handler is a Spring
 * bean and the entity is picked up from its stereotype.
 */
object ExpireHoldConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val stateEntity = EventSourcedEntityModule.autodetected(ExpireLedger::class.java, ExpireState::class.java)
        val commandHandlingModule =
            CommandHandlingModule
                .named("ExpireHold")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { ExpireHoldCommandHandler(clock) }
        return configurer
            .registerEntity(stateEntity)
            .registerCommandHandlingModule(commandHandlingModule)
    }
}
