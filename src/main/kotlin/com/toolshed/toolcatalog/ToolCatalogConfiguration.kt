package com.toolshed.toolcatalog

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule

/**
 * Wiring for this view alone, so a test can build the fixture from the same registrations. The projection runs on
 * the foundation's token store (the streaming processor), and the query handler answers from the same repository.
 * In the running app the beans are picked up by annotation scanning.
 */
object ToolCatalogConfiguration {
    fun configure(configurer: EventSourcingConfigurer): EventSourcingConfigurer {
        val repository = ToolCatalogRepository()
        val projection =
            EventProcessorModule
                .pooledStreaming("ToolCatalogProjection")
                .eventHandlingComponents { it.autodetected { ToolCatalogProjection(repository) } }
                .notCustomized()
        val queryHandling =
            QueryHandlingModule
                .named("ListToolCatalog")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { ToolCatalogQueryHandler(repository) }

        return configurer
            .registerQueryHandlingModule(queryHandling)
            .modelling { m ->
                m.messaging { ms ->
                    ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projection) } }
                }
            }
    }
}
