package com.toolshed.overdueloanstonotify

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import java.time.Clock

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations. In the running app the
 * projection and query handler are Spring beans, auto-detected from their annotations.
 */
object OverdueLoansToNotifyConfiguration {
    private const val PROCESSOR_NAME = "Projection_OverdueLoansToNotify_Processor"

    fun configure(
        configurer: EventSourcingConfigurer,
        repository: OverdueLoansToNotifyRepository,
        clock: Clock,
    ): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .pooledStreaming(PROCESSOR_NAME)
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
