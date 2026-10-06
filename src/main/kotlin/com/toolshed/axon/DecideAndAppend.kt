package com.toolshed.axon

import org.axonframework.messaging.core.Context
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.messaging.eventhandling.gateway.EventAppender

/** Marks a processing context whose events have been appended. Owned by the foundation, not by Axon. */
val APPENDED: Context.ResourceKey<Boolean> = Context.ResourceKey.withLabel("toolshed.appended")

/**
 * The only way a command handler appends events. [decide] runs first; it may throw a CommandRejected, in which case
 * nothing is appended. Its events are appended only when it returns normally, and the context is marked so that a
 * later refusal in the same command fails closed (see CommandRejectionInterceptor).
 */
fun <E : Any> decideAndAppend(
    context: ProcessingContext,
    appender: EventAppender,
    decide: () -> List<E>,
): List<E> {
    val events = decide()
    if (events.isNotEmpty()) {
        context.putResource(APPENDED, true)
        appender.append(events)
    }
    return events
}
