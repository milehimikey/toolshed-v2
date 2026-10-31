package com.toolshed.flaggedneighbors

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations. In the running app
 * the projection and query handler are Spring beans, auto-detected from their annotations.
 */
object FlaggedNeighborsConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        repository: FlaggedNeighborsRepository,
    ): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .pooledStreaming("Projection_FlaggedNeighbors_Processor")
                .eventHandlingComponents { it.autodetected { FlaggedNeighborsProjection(repository) } }
                .notCustomized()

        val queryHandlingModule =
            QueryHandlingModule
                .named("ListFlaggedNeighbors")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { ListFlaggedNeighborsQueryHandler(repository) }

        return configurer
            .registerQueryHandlingModule(queryHandlingModule)
            .modelling { m ->
                m.messaging { ms -> ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projectionProcessor) } } }
            }
    }
}
