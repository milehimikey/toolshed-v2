package com.toolshed.toolinventory

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import java.time.Clock

/**
 * Wiring for this view alone, so a test can build the fixture from the same registrations. The projection runs on
 * the foundation's token store (the streaming processor), and the query handler answers from the same repository.
 * In the running app the beans are picked up by annotation scanning.
 */
object ToolInventoryConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val repository = ToolInventoryRepository()
        val projection =
            EventProcessorModule
                .pooledStreaming("ToolInventoryProjection")
                .eventHandlingComponents { it.autodetected { ToolInventoryProjection(repository) } }
                .notCustomized()
        val queryHandling =
            QueryHandlingModule
                .named("ListToolInventory")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { ToolInventoryQueryHandler(repository, clock) }

        return configurer
            .registerQueryHandlingModule(queryHandling)
            .modelling { m ->
                m.messaging { ms ->
                    ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projection) } }
                }
            }
    }
}
