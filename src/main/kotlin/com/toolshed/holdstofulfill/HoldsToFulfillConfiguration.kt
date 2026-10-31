package com.toolshed.holdstofulfill

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations. In the running app
 * the projection and query handler are Spring beans, auto-detected from their annotations.
 */
object HoldsToFulfillConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        repository: HoldsToFulfillRepository,
    ): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .pooledStreaming("Projection_HoldsToFulfill_Processor")
                .eventHandlingComponents { it.autodetected { HoldsToFulfillProjection(repository) } }
                .notCustomized()

        val queryHandlingModule =
            QueryHandlingModule
                .named("ListToolsToFulfill")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { ListToolsToFulfillQueryHandler(repository) }

        return configurer
            .registerQueryHandlingModule(queryHandlingModule)
            .modelling { m ->
                m.messaging { ms -> ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projectionProcessor) } } }
            }
    }
}
