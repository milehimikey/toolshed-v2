package com.toolshed.neighborstoflag

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import java.time.Clock

/** Wiring for this view alone, so a test can build the fixture from the same registrations. */
object NeighborsToFlagConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val repository = NeighborsToFlagRepository()
        val projection =
            EventProcessorModule
                .pooledStreaming("NeighborsToFlagProjection")
                .eventHandlingComponents { it.autodetected { NeighborsToFlagProjection(repository) } }
                .notCustomized()
        val queryHandling =
            QueryHandlingModule
                .named("ListNeighborsToFlag")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { NeighborsToFlagQueryHandler(repository, clock) }

        return configurer
            .registerQueryHandlingModule(queryHandling)
            .modelling { m ->
                m.messaging { ms ->
                    ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projection) } }
                }
            }
    }
}
