package com.toolshed.overdueloanstonotify

import com.toolshed.axon.dedicatedProcessor
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.extension.spring.config.EventProcessorDefinition
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations. In the running app the
 * projection and query handler are Spring beans, auto-detected from their annotations.
 */
object OverdueLoansToNotifyConfiguration {
    /** The projection's own pooled-streaming processor, in the fixture and in the running app alike. */
    const val PROCESSOR = "Projection_OverdueLoansToNotify_Processor"

    fun configure(
        configurer: EventSourcingConfigurer,
        repository: OverdueLoansToNotifyRepository,
        clock: Clock,
    ): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .pooledStreaming(PROCESSOR)
                .eventHandlingComponents { components ->
                    components.autodetected { OverdueLoansToNotifyProjection(repository) }
                }.notCustomized()

        val queryHandlingModule =
            QueryHandlingModule
                .named("ListOverdueLoansToNotify")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { OverdueLoansToNotifyQueryHandler(repository, clock) }

        return configurer
            .registerQueryHandlingModule(queryHandlingModule)
            .modelling { m ->
                m.messaging { ms -> ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projectionProcessor) } } }
            }
    }
}

/** The running app's processor for the projection: the fixture's name, the projection alone (see [dedicatedProcessor]). */
@Configuration
class OverdueLoansToNotifyProjectionProcessorConfiguration {
    @Bean
    fun overdueLoansToNotifyProjectionProcessor(): EventProcessorDefinition =
        dedicatedProcessor(OverdueLoansToNotifyConfiguration.PROCESSOR, OverdueLoansToNotifyProjection::class.java)
}
