package com.toolshed.sendoverduenotice

import com.toolshed.axon.EmailGateway
import com.toolshed.axon.dedicatedProcessor
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.extension.spring.config.EventProcessorDefinition
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Wiring for this slice alone, so a test can build the fixture from the same registrations: the command side, and the outbox
 * on its own processor. In the running app the handlers are Spring beans and the entity is picked up from its stereotype;
 * the outbox's processor is pinned by [OverdueNoticeOutboxProcessorConfiguration] to the same name the fixture uses.
 */
object SendOverdueNoticeConfiguration {
    /** The outbox's own pooled-streaming processor, in the fixture and in the running app alike. */
    const val OUTBOX_PROCESSOR = "SendOverdueNotice_Outbox_Processor"

    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val entity = EventSourcedEntityModule.autodetected(OverdueNoticeKey::class.java, SendOverdueNoticeState::class.java)
        val commandHandling =
            CommandHandlingModule
                .named("SendOverdueNotice")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { SendOverdueNoticeCommandHandler(clock) }
        return configurer
            .registerEntity(entity)
            .registerCommandHandlingModule(commandHandling)
    }

    /** The outbox: Overdue Notice Sent handed to [emailGateway], repeated until accepted (INV-OVN-4). */
    fun configureOutbox(
        configurer: EventSourcingConfigurer,
        emailGateway: EmailGateway,
    ): EventSourcingConfigurer {
        val outbox =
            EventProcessorModule
                .pooledStreaming(OUTBOX_PROCESSOR)
                .eventHandlingComponents { components -> components.autodetected { OverdueNoticeOutbox(emailGateway) } }
                .notCustomized()
        return configurer.modelling { m ->
            m.messaging { ms -> ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(outbox) } } }
        }
    }
}

/**
 * The running app's processor for the outbox. Without this, Axon's Spring starter would put the outbox on a processor
 * named after its package, shared with any other event handler that lands there. A refused hand-off stops the outbox's
 * processor on purpose (INV-OVN-4), so it must stall nothing else: the outbox, and only the outbox, rides this one.
 */
@Configuration
class OverdueNoticeOutboxProcessorConfiguration {
    @Bean
    fun overdueNoticeOutboxProcessor(): EventProcessorDefinition =
        dedicatedProcessor(SendOverdueNoticeConfiguration.OUTBOX_PROCESSOR, OverdueNoticeOutbox::class.java)
}
