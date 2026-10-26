package com.toolshed.myholds

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import java.time.Clock

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations. In the running app
 * the projection and query handler are Spring beans, auto-detected from their annotations.
 */
object MyHoldsConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        repository: MyHoldsRepository,
        clock: Clock,
    ): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .pooledStreaming("Projection_MyHolds_Processor")
                .eventHandlingComponents { it.autodetected { MyHoldsProjection(repository) } }
                .notCustomized()

        val queryHandlingModule =
            QueryHandlingModule
                .named("ListMyHolds")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { ListMyHoldsQueryHandler(repository, clock) }

        return configurer
            .registerQueryHandlingModule(queryHandlingModule)
            .modelling { m ->
                m.messaging { ms -> ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projectionProcessor) } } }
            }
    }
}
