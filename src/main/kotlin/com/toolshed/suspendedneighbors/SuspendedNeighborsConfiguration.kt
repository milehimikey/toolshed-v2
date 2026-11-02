package com.toolshed.suspendedneighbors

import com.toolshed.axon.dedicatedProcessor
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.extension.spring.config.EventProcessorDefinition
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations. In the running app
 * the projection and query handler are Spring beans, auto-detected from their annotations.
 */
object SuspendedNeighborsConfiguration {
    /** The projection's own pooled-streaming processor, in the fixture and in the running app alike. */
    const val PROCESSOR = "Projection_SuspendedNeighbors_Processor"

    fun configure(
        configurer: EventSourcingConfigurer,
        repository: SuspendedNeighborsRepository,
    ): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .pooledStreaming(PROCESSOR)
                .eventHandlingComponents { it.autodetected { SuspendedNeighborsProjection(repository) } }
                .notCustomized()

        val queryHandlingModule =
            QueryHandlingModule
                .named("ListSuspendedNeighbors")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { ListSuspendedNeighborsQueryHandler(repository) }

        return configurer
            .registerQueryHandlingModule(queryHandlingModule)
            .modelling { m ->
                m.messaging { ms -> ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projectionProcessor) } } }
            }
    }
}

/** The running app's processor for the projection: the fixture's name, the projection alone (see [dedicatedProcessor]). */
@Configuration
class SuspendedNeighborsProjectionProcessorConfiguration {
    @Bean
    fun suspendedNeighborsProjectionProcessor(): EventProcessorDefinition =
        dedicatedProcessor(SuspendedNeighborsConfiguration.PROCESSOR, SuspendedNeighborsProjection::class.java)
}
