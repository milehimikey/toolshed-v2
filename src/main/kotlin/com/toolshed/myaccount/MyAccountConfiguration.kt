package com.toolshed.myaccount

import com.toolshed.axon.dedicatedProcessor
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.extension.spring.config.EventProcessorDefinition
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wiring for this view alone, so a test can build the fixture from the same registrations. In the running app
 * the beans are picked up by annotation scanning.
 */
object MyAccountConfiguration {
    /** The projection's own pooled-streaming processor, in the fixture and in the running app alike. */
    const val PROCESSOR = "Projection_MyAccount_Processor"

    fun configure(
        configurer: EventSourcingConfigurer,
        repository: MyAccountRepository = MyAccountRepository(),
    ): EventSourcingConfigurer {
        val projection =
            EventProcessorModule
                .pooledStreaming(PROCESSOR)
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

/** The running app's processor for the projection: the fixture's name, the projection alone (see [dedicatedProcessor]). */
@Configuration
class MyAccountProjectionProcessorConfiguration {
    @Bean
    fun myAccountProjectionProcessor(): EventProcessorDefinition =
        dedicatedProcessor(MyAccountConfiguration.PROCESSOR, MyAccountProjection::class.java)
}
