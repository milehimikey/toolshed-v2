package com.toolshed.holdstofulfill

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
object HoldsToFulfillConfiguration {
    /** The projection's own pooled-streaming processor, in the fixture and in the running app alike. */
    const val PROCESSOR = "Projection_HoldsToFulfill_Processor"

    /** [reactions] are handed to the projection, which invokes each after its repository write (Amendment 11). */
    fun configure(
        configurer: EventSourcingConfigurer,
        repository: HoldsToFulfillRepository,
        reactions: List<HoldsToFulfillReaction> = emptyList(),
    ): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .pooledStreaming(PROCESSOR)
                .eventHandlingComponents { components ->
                    components.autodetected { HoldsToFulfillProjection(repository, reactions) }
                }.notCustomized()

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

/** The running app's processor for the projection: the fixture's name, the projection alone (see [dedicatedProcessor]). */
@Configuration
class HoldsToFulfillProjectionProcessorConfiguration {
    @Bean
    fun holdsToFulfillProjectionProcessor(): EventProcessorDefinition =
        dedicatedProcessor(HoldsToFulfillConfiguration.PROCESSOR, HoldsToFulfillProjection::class.java)
}
