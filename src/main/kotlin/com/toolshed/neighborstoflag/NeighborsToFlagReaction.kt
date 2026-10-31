package com.toolshed.neighborstoflag

import org.axonframework.extension.spring.config.EventHandlerSelector
import org.axonframework.extension.spring.config.EventProcessorDefinition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Marks an automation that reads Neighbors To Flag. Such a reaction runs on this list's own processor, after the projection
 * (Amendment 11), so the list has applied an event before the reaction reads it.
 */
interface NeighborsToFlagReaction

/** Binds the projection and every [NeighborsToFlagReaction] bean to the one processor `NeighborsToFlagConfiguration` names. */
@Configuration
class NeighborsToFlagWiring {
    @Bean
    fun neighborsToFlagProcessor(): EventProcessorDefinition =
        EventProcessorDefinition
            .pooledStreaming(PROCESSOR_NAME)
            .assigningHandlers(
                EventHandlerSelector { bean ->
                    NeighborsToFlagProjection::class.java.isAssignableFrom(bean.beanType()) ||
                        NeighborsToFlagReaction::class.java.isAssignableFrom(bean.beanType())
                },
            ).notCustomized()

    companion object {
        const val PROCESSOR_NAME = "NeighborsToFlagProjection"
    }
}
