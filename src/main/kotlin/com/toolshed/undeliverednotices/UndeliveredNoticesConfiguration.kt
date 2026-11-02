package com.toolshed.undeliverednotices

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
object UndeliveredNoticesConfiguration {
    /** The projection's own pooled-streaming processor, in the fixture and in the running app alike. */
    const val PROCESSOR = "Projection_UndeliveredNotices_Processor"

    fun configure(
        configurer: EventSourcingConfigurer,
        repository: UndeliveredNoticesRepository,
    ): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .pooledStreaming(PROCESSOR)
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

/** The running app's processor for the projection: the fixture's name, the projection alone (see [dedicatedProcessor]). */
@Configuration
class UndeliveredNoticesProjectionProcessorConfiguration {
    @Bean
    fun undeliveredNoticesProjectionProcessor(): EventProcessorDefinition =
        dedicatedProcessor(UndeliveredNoticesConfiguration.PROCESSOR, UndeliveredNoticesProjection::class.java)
}
