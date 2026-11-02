package com.toolshed.axon

import org.axonframework.extension.spring.config.EventProcessorDefinition

/**
 * The running app's processor definition for one event handling component: a pooled-streaming processor under the same
 * [name] the slice's fixture uses, carrying [handlerType] and nothing else. Without it, Axon's Spring starter names the
 * processor after the component's package and shares it with whatever else handles events there, so fixture and app would
 * run the same handler under different names, and a handler that stops its processor on purpose (an outbox) could stall
 * a neighbour.
 */
fun dedicatedProcessor(
    name: String,
    handlerType: Class<*>,
): EventProcessorDefinition =
    EventProcessorDefinition
        .pooledStreaming(name)
        .assigningHandlers { handler -> handler.beanType()?.let { handlerType.isAssignableFrom(it) } ?: false }
        .notCustomized()
