package com.toolshed.neighborstoflag

import com.toolshed.axon.dedicatedProcessor
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.extension.spring.config.EventProcessorDefinition
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Wiring for this view alone, so a test can build the fixture from the same registrations. [reactions] are the automations
 * that read this list; the projection invokes each after its repository write (Amendment 11).
 */
object NeighborsToFlagConfiguration {
    /** The projection's own pooled-streaming processor, in the fixture and in the running app alike. */
    const val PROCESSOR = "Projection_NeighborsToFlag_Processor"

    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
        reactions: List<NeighborsToFlagReaction> = emptyList(),
    ): EventSourcingConfigurer {
        val repository = NeighborsToFlagRepository()
        val projection =
            EventProcessorModule
                .pooledStreaming(PROCESSOR)
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

/** The running app's processor for the projection: the fixture's name, the projection alone (see [dedicatedProcessor]). */
@Configuration
class NeighborsToFlagProjectionProcessorConfiguration {
    @Bean
    fun neighborsToFlagProjectionProcessor(): EventProcessorDefinition =
        dedicatedProcessor(NeighborsToFlagConfiguration.PROCESSOR, NeighborsToFlagProjection::class.java)
}
