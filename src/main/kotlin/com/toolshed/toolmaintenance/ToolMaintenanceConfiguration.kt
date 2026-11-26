package com.toolshed.toolmaintenance

import com.toolshed.axon.dedicatedProcessor
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.extension.spring.config.EventProcessorDefinition
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wiring for this view alone, so a test can build the fixture from the same registrations. The projection runs on
 * its own pooled-streaming processor and the query handler answers from the same repository.
 */
object ToolMaintenanceConfiguration {
    const val PROCESSOR = "Projection_ToolMaintenance_Processor"

    fun configure(configurer: EventSourcingConfigurer): EventSourcingConfigurer {
        val repository = ToolMaintenanceRepository()
        val projection =
            EventProcessorModule
                .pooledStreaming(PROCESSOR)
                .eventHandlingComponents { it.autodetected { ToolMaintenanceProjection(repository) } }
                .notCustomized()
        val queryHandling =
            QueryHandlingModule
                .named("ListToolsInMaintenance")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { ToolMaintenanceQueryHandler(repository) }

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
class ToolMaintenanceProjectionProcessorConfiguration {
    @Bean
    fun toolMaintenanceProjectionProcessor(): EventProcessorDefinition =
        dedicatedProcessor(ToolMaintenanceConfiguration.PROCESSOR, ToolMaintenanceProjection::class.java)
}
