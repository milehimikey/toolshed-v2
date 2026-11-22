package com.toolshed.scheduletoolmaintenance

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import java.time.Clock

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations.
 * In the running app the handler is a Spring bean and the State entity is picked up from its
 * @EventSourced stereotype.
 */
object ScheduleToolMaintenanceConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val stateEntity =
            EventSourcedEntityModule.autodetected(
                MaintenanceLedger::class.java,
                ScheduleMaintenanceState::class.java,
            )
        val commandHandlingModule =
            CommandHandlingModule
                .named("ScheduleToolMaintenance")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { ScheduleToolMaintenanceCommandHandler(clock) }

        return configurer
            .registerEntity(stateEntity)
            .registerCommandHandlingModule(commandHandlingModule)
    }
}
