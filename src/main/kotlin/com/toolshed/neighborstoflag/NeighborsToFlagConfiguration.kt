package com.toolshed.neighborstoflag

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import java.time.Clock

/**
 * Wiring for this view alone, so a test can build the fixture from the same registrations. [reactions] are the automations
 * that read this list; the projection invokes each after its repository write (Amendment 11).
 */
object NeighborsToFlagConfiguration {
    private const val PROCESSOR_NAME = "NeighborsToFlagProjection"

    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
        reactions: List<NeighborsToFlagReaction> = emptyList(),
    ): EventSourcingConfigurer {
        val repository = NeighborsToFlagRepository()
        val projection =
            EventProcessorModule
                .pooledStreaming(PROCESSOR_NAME)
                .eventHandlingComponents { it.autodetected { NeighborsToFlagProjection(repository, reactions) } }
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
