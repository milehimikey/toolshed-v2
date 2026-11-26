package com.toolshed.myaccount

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule

/**
 * Wiring for this view alone, so a test can build the fixture from the same registrations. The projection runs on
 * the foundation's token store (the streaming processor), and the query handler answers from the same repository.
 * In the running app the beans are picked up by annotation scanning.
 */
object MyAccountConfiguration {
    fun configure(configurer: EventSourcingConfigurer): EventSourcingConfigurer {
        val repository = MyAccountRepository()
        val projection =
            EventProcessorModule
                .pooledStreaming("MyAccountProjection")
                .eventHandlingComponents { it.autodetected { MyAccountProjection(repository) } }
                .notCustomized()
        val queryHandling =
            QueryHandlingModule
                .named("GetMyAccount")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { MyAccountQueryHandler(repository) }

        return configurer
            .registerQueryHandlingModule(queryHandling)
            .modelling { m ->
                m.messaging { ms ->
                    ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projection) } }
                }
            }
    }
}
