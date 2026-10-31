package com.toolshed.neighborstoflag

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import java.time.Clock

/**
 * Wiring for this view alone, so a test can build the fixture from the same registrations. [reactions] are the automations
 * that read this list; each is registered on this processor after the projection (Amendment 11).
 */
object NeighborsToFlagConfiguration {
    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
        reactions: List<Any> = emptyList(),
    ): EventSourcingConfigurer {
        val repository = NeighborsToFlagRepository()
        val projection =
            EventProcessorModule
                .pooledStreaming(NeighborsToFlagWiring.PROCESSOR_NAME)
                .eventHandlingComponents { phase ->
                    // Projection first, then each reaction: handling components run in registration order (Amendment 11).
                    reactions.fold(phase.autodetected { NeighborsToFlagProjection(repository) }) { components, reaction ->
                        components.autodetected { reaction }
                    }
                }.notCustomized()
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
