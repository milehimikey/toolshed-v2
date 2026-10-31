package com.toolshed.holdstofulfill

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations. In the running app
 * the projection and query handler are Spring beans, auto-detected from their annotations.
 */
object HoldsToFulfillConfiguration {
    private const val PROCESSOR_NAME = "Projection_HoldsToFulfill_Processor"

    /** [reactions] are handed to the projection, which invokes each after its repository write (Amendment 11). */
    fun configure(
        configurer: EventSourcingConfigurer,
        repository: HoldsToFulfillRepository,
        reactions: List<HoldsToFulfillReaction> = emptyList(),
    ): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .pooledStreaming(PROCESSOR_NAME)
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
