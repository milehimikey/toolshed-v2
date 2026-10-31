package com.toolshed.holdstofulfill

import org.axonframework.extension.spring.config.EventProcessorDefinition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Marks an automation's event handler as one that reads Holds To Fulfill and so rides its processor (Amendment 11).
 * Such a handler is registered on the list's processor after the projection, so the list has applied each event
 * before the reaction reads it.
 */
interface HoldsToFulfillReaction

/** Assigns the projection and every [HoldsToFulfillReaction] bean to the one processor [HoldsToFulfillConfiguration] names. */
@Configuration
class HoldsToFulfillProcessorDefinition {
    @Bean
    fun holdsToFulfillProcessor(): EventProcessorDefinition =
        EventProcessorDefinition
            .pooledStreaming(HoldsToFulfillConfiguration.PROCESSOR_NAME)
            .assigningHandlers {
                HoldsToFulfillProjection::class.java == it.beanType() || HoldsToFulfillReaction::class.java.isAssignableFrom(it.beanType())
            }.notCustomized()
}
