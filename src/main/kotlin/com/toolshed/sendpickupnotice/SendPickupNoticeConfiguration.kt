package com.toolshed.sendpickupnotice

import com.toolshed.axon.EmailGateway
import com.toolshed.axon.ReactionDispatcher
import com.toolshed.pickupnoticestosend.PickupNoticesToSendRepository
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
 * on its own processor. The gateway's reading half has no processor of its own (Amendment 11): it rides Pickup Notices To
 * Send's. In the running app the handlers are Spring beans and the entity is picked up from its stereotype; the outbox's
 * processor is pinned by [PickupNoticeOutboxProcessorConfiguration] to the same name the fixture uses.
 */
object SendPickupNoticeConfiguration {
    /** The outbox's own pooled-streaming processor, in the fixture and in the running app alike. */
    const val OUTBOX_PROCESSOR = "SendPickupNotice_Outbox_Processor"

    fun configure(
        configurer: EventSourcingConfigurer,
        clock: Clock,
    ): EventSourcingConfigurer {
        val entity = EventSourcedEntityModule.autodetected(PickupNoticeKey::class.java, SendPickupNoticeState::class.java)
        val commandHandling =
            CommandHandlingModule
                .named("SendPickupNotice")
                .commandHandlers()
                .autodetectedCommandHandlingComponent { SendPickupNoticeCommandHandler(clock) }
        return configurer
            .registerEntity(entity)
            .registerCommandHandlingModule(commandHandling)
    }

    /** The outbox: Pickup Notice Sent handed to [emailGateway], repeated until accepted (INV-PKN-4). */
    fun configureOutbox(
        configurer: EventSourcingConfigurer,
        emailGateway: EmailGateway,
    ): EventSourcingConfigurer {
        val outbox =
            EventProcessorModule
                .pooledStreaming(OUTBOX_PROCESSOR)
                .eventHandlingComponents { components -> components.autodetected { PickupNoticeOutbox(emailGateway) } }
                .notCustomized()
        return configurer.modelling { m ->
            m.messaging { ms -> ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(outbox) } } }
        }
    }
}

/**
 * The running app's processor for the outbox. Without this, Axon's Spring starter would put the outbox on a processor
 * named after its package, shared with any other event handler that lands there. A refused hand-off stops the outbox's
 * processor on purpose (INV-PKN-4), so it must stall nothing else: the outbox, and only the outbox, rides this one.
 */
@Configuration
class PickupNoticeOutboxProcessorConfiguration {
    @Bean
    fun pickupNoticeOutboxProcessor(): EventProcessorDefinition =
        EventProcessorDefinition
            .pooledStreaming(SendPickupNoticeConfiguration.OUTBOX_PROCESSOR)
            .assigningHandlers { handler -> handler.beanType()?.let { PickupNoticeOutbox::class.java.isAssignableFrom(it) } ?: false }
            .notCustomized()
}

/** The running app's Pickup Notice Gateway: reads the list in-process from the read slice's repository (Amendment 11). */
@Configuration
class PickupNoticeGatewayConfiguration {
    @Bean
    fun pickupNoticeGateway(
        repository: PickupNoticesToSendRepository,
        dispatcher: ReactionDispatcher,
    ): PickupNoticeGateway =
        PickupNoticeGateway(
            list = { repository.pickupsToNotify() },
            send = { dispatcher.send(it) },
        )

    /** Spring injects this into the Pickup Notices To Send projection, which invokes it after each list change. */
    @Bean
    fun pickupNoticeGatewayHandler(gateway: PickupNoticeGateway): PickupNoticeGatewayHandler = PickupNoticeGatewayHandler(gateway)
}
