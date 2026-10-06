package com.toolshed.recordpickupnoticefailure

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock

/** Wires the Record Pickup Notice Failure slice for the fixture: its entity and its command handler. */
object RecordPickupNoticeFailureConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val entity = EventSourcedEntityModule.autodetected(PickupNoticeKey::class.java, PickupNoticeState::class.java)
        val commandHandling =
            CommandHandlingModule
                .named("RecordPickupNoticeFailure")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { RecordPickupNoticeFailureCommandHandler(clock) }
                .build()
        return configurer
            .registerEntity(entity)
            .registerCommandHandlingModule(commandHandling)
    }
}
