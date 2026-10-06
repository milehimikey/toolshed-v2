package com.toolshed.axon

import org.axonframework.messaging.core.Context
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.messaging.eventhandling.gateway.EventAppender

/** Marks a processing context whose events have been appended. Owned by the foundation, not by Axon. */
val APPENDED: Context.ResourceKey<Boolean> = Context.ResourceKey.withLabel("toolshed.appended")

/**
 * The only way a command handler appends events, and the documented one: call it on the handler's ProcessingContext.
 * [decide] runs first; it may throw a CommandRejected, in which case nothing is appended. Its events are appended only
 * when it returns normally, and the context is marked so that a later refusal in the same command fails closed (see
 * CommandRejectionInterceptor). The appender is resolved from the context here, so a slice never names EventAppender.
 */
fun <E : Any> ProcessingContext.decideAndAppend(decide: () -> List<E>): List<E> =
    decideAndAppend(this, EventAppender.forContext(this), decide)

/** Internal: the runner with an explicit appender. Slices use the context form above. */
internal fun <E : Any> decideAndAppend(
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
