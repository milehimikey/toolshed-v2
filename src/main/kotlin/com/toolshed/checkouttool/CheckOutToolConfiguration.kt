package com.toolshed.checkouttool

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock

/** Wires the Check Out Tool slice: its entity and its command handler. Nothing else. */
object CheckOutToolConfiguration {
    private fun entity() = EventSourcedEntityModule.autodetected(CheckoutScope::class.java, CheckOutState::class.java)

    private fun commandHandling(clock: Clock) =
        CommandHandlingModule
            .named("CheckOutTool")
            .commandHandlers()
            .autodetectedCommandHandlingComponent { CheckOutToolCommandHandler(clock) }
            .build()

    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer =
        configurer
            .registerEntity(entity())
            .registerCommandHandlingModule(commandHandling(clock))
}
