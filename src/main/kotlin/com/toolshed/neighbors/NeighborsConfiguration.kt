package com.toolshed.neighbors

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations. In the running app
 * the projection and query handler are Spring beans, auto-detected from their annotations.
 */
object NeighborsConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        repository: NeighborsRepository,
    ): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .pooledStreaming("Projection_Neighbors_Processor")
                .eventHandlingComponents { it.autodetected { NeighborsProjection(repository) } }
                .notCustomized()

        val queryHandlingModule =
            QueryHandlingModule
                .named("ListNeighbors")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { ListNeighborsQueryHandler(repository) }

        return configurer
            .registerQueryHandlingModule(queryHandlingModule)
            .modelling { m ->
                m.messaging { ms -> ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projectionProcessor) } } }
            }
    }
}
