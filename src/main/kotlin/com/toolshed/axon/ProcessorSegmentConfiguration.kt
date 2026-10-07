package com.toolshed.axon

import org.axonframework.extension.spring.config.EventProcessorDefinition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Every pooled-streaming processor runs one segment (the view-concurrency ruling). One thread applies events in global
 * order, which views that combine events across tools, loans and holds need. Throughput is trivial at shed scale, so
 * the single segment costs nothing; the claim cap stops the processor splitting itself later.
 */
@Configuration
class ProcessorSegmentConfiguration {
    @Bean
    fun singleSegmentProcessors(): EventProcessorDefinition =
        EventProcessorDefinition
            .pooledStreaming("single-segment")
            .assigningHandlers { true }
            .customized { it.initialSegmentCount(1).maxClaimedSegments(1) }
}
