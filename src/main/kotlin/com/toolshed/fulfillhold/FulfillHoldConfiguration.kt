package com.toolshed.fulfillhold

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations: the command side. The Hold
 * Fulfiller has no processor of its own (Amendment 11): it rides Holds To Fulfill's. In the running app the handlers are
 * Spring beans and the entity is picked up from its stereotype.
 */
object FulfillHoldConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val stateEntity = EventSourcedEntityModule.autodetected(FulfillLedger::class.java, FulfillState::class.java)
        val commandHandlingModule =
            CommandHandlingModule
                .named("FulfillHold")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { FulfillHoldCommandHandler(clock) }
        return configurer
            .registerEntity(stateEntity)
            .registerCommandHandlingModule(commandHandlingModule)
    }
}
