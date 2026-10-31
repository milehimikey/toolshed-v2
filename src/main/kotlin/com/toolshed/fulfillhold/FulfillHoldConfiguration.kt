package com.toolshed.fulfillhold

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import java.time.Clock

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations: the command side, and the
 * Hold Fulfiller's processor. In the running app the handlers are Spring beans and the entity is picked up from its stereotype.
 */
object FulfillHoldConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
        fulfiller: HoldFulfiller,
    ): EventSourcingConfigurer {
        val stateEntity = EventSourcedEntityModule.autodetected(FulfillLedger::class.java, FulfillState::class.java)
        val commandHandlingModule =
            CommandHandlingModule
                .named("FulfillHold")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { FulfillHoldCommandHandler(clock) }
        val fulfillerProcessor =
            EventProcessorModule
                .pooledStreaming("Automation_HoldFulfiller_Processor")
                .eventHandlingComponents { it.autodetected { HoldFulfillerHandler(fulfiller) } }
                .notCustomized()

        return configurer
            .registerEntity(stateEntity)
            .registerCommandHandlingModule(commandHandlingModule)
            .modelling { m ->
                m.messaging { ms -> ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(fulfillerProcessor) } } }
            }
    }
}
