package com.toolshed.undeliverednotices

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations. In the running app
 * the projection and query handler are Spring beans, auto-detected from their annotations.
 */
object UndeliveredNoticesConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        repository: UndeliveredNoticesRepository,
    ): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .pooledStreaming("Projection_UndeliveredNotices_Processor")
                .eventHandlingComponents { it.autodetected { UndeliveredNoticesProjection(repository) } }
                .notCustomized()

        val queryHandlingModule =
            QueryHandlingModule
                .named("ListUndeliveredNotices")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { ListUndeliveredNoticesQueryHandler(repository) }

        return configurer
            .registerQueryHandlingModule(queryHandlingModule)
            .modelling { m ->
                m.messaging { ms -> ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projectionProcessor) } } }
            }
    }
}
