package com.toolshed.toolinventory

import com.toolshed.axon.dedicatedProcessor
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.extension.spring.config.EventProcessorDefinition
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Wiring for this view alone, so a test can build the fixture from the same registrations. The projection runs on
 * the foundation's token store (the streaming processor), and the query handler answers from the same repository.
 * In the running app the beans are picked up by annotation scanning.
 */
object ToolInventoryConfiguration {
    /** The projection's own pooled-streaming processor, in the fixture and in the running app alike. */
    const val PROCESSOR = "Projection_ToolInventory_Processor"

    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val repository = ToolInventoryRepository()
        val projection =
            EventProcessorModule
                .pooledStreaming(PROCESSOR)
                .eventHandlingComponents { it.autodetected { ToolInventoryProjection(repository) } }
                .notCustomized()
        val queryHandling =
            QueryHandlingModule
                .named("ListToolInventory")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { ToolInventoryQueryHandler(repository, clock) }

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
class ToolInventoryProjectionProcessorConfiguration {
    @Bean
    fun toolInventoryProjectionProcessor(): EventProcessorDefinition =
        dedicatedProcessor(ToolInventoryConfiguration.PROCESSOR, ToolInventoryProjection::class.java)
}
