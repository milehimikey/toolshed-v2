package com.toolshed.myaccount

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule

/**
 * Wires My Account for the fixture: the repository, the projection's processor, and the query's handler. The projection is a
 * subscribing processor here, so each event is applied as it is published and a query-bus test reads the result straight away.
 */
object MyAccountConfiguration {
    fun configure(configurer: EventSourcingConfigurer): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .subscribing("MyAccountProjection")
                .eventHandlingComponents { c ->
                    c.autodetected { cfg -> MyAccountProjection(cfg.getComponent(MyAccountRepository::class.java)) }
                }.notCustomized()

        val getQuery =
            QueryHandlingModule
                .named("get-my-account")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { cfg ->
                    MyAccountQueryHandler(MyAccountView(cfg.getComponent(MyAccountRepository::class.java)))
                }.build()

        return configurer
            .componentRegistry { cr -> cr.registerComponent(MyAccountRepository::class.java) { MyAccountRepository() } }
            .registerQueryHandlingModule(getQuery)
            .messaging { m -> m.eventProcessing { ep -> ep.subscribing { s -> s.processor(projectionProcessor) } } }
    }
}
