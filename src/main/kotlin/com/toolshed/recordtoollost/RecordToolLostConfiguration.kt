package com.toolshed.recordtoollost

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock

/** Wires the Record Tool Lost slice for the fixture: its entity and its command handler. */
object RecordToolLostConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val entity = EventSourcedEntityModule.autodetected(LoanKey::class.java, LoanState::class.java)
        val commandHandling =
            CommandHandlingModule
                .named("RecordToolLost")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { RecordToolLostCommandHandler(clock) }
                .build()
        return configurer
            .registerEntity(entity)
            .registerCommandHandlingModule(commandHandling)
    }
}
