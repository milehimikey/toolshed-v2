package com.toolshed.neighbors

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule

/**
 * Wires the Neighbors view for the fixture: the repository, the projection's processor, and the list query's handler.
 * The projection is a subscribing processor here, so each event is applied as it is published and a query-bus test can
 * read the result straight away. Production wiring is the Spring-detected projection and query handler in this package.
 */
object NeighborsConfiguration {
    fun configure(configurer: EventSourcingConfigurer): EventSourcingConfigurer {
        val projectionProcessor =
            EventProcessorModule
                .subscribing("NeighborsProjection")
                .eventHandlingComponents { c ->
                    c.autodetected { cfg -> NeighborsProjection(cfg.getComponent(NeighborsRepository::class.java)) }
                }.notCustomized()

        val listQuery =
            QueryHandlingModule
                .named("list-neighbors")
                .queryHandlers()
                .autodetectedQueryHandlingComponent { cfg ->
                    NeighborsQueryHandler(NeighborsView(cfg.getComponent(NeighborsRepository::class.java)))
                }.build()

        return configurer
            .componentRegistry { cr -> cr.registerComponent(NeighborsRepository::class.java) { NeighborsRepository() } }
            .registerQueryHandlingModule(listQuery)
            .messaging { m -> m.eventProcessing { ep -> ep.subscribing { s -> s.processor(projectionProcessor) } } }
    }
}
