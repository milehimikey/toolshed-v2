package com.toolshed.axon

import org.axonframework.messaging.commandhandling.CommandMessage
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage
import org.axonframework.messaging.core.Message
import org.axonframework.messaging.core.MessageHandlerInterceptor
import org.axonframework.messaging.core.MessageHandlerInterceptorChain
import org.axonframework.messaging.core.MessageStream
import org.axonframework.messaging.core.MessageType
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.springframework.stereotype.Component

/**
 * Turns a CommandRejected thrown in a handler into a [CommandRefusal] result, so the refusal survives the trip
 * through Axon Server as a normal payload. Anything that is not a refusal passes through unchanged.
 */
@Component
class CommandRejectionInterceptor : MessageHandlerInterceptor<CommandMessage> {
    @Suppress("UNCHECKED_CAST")
    override fun interceptOnHandle(
        message: CommandMessage,
        context: ProcessingContext,
        chain: MessageHandlerInterceptorChain<CommandMessage>,
    ): MessageStream<*> {
        // The chain yields the command's result messages; the cast only names that type for the stream below.
        val results = chain.proceed(message, context) as MessageStream<Message>
        return results.onErrorContinue { error ->
            val rejected =
                generateSequence(error) { it.cause }.filterIsInstance<CommandRejected>().firstOrNull()
                    ?: return@onErrorContinue MessageStream.failed(error)
            val refusal = CommandRefusal(rejected.invariant, rejected.reason, rejected.message.orEmpty())
            MessageStream.fromItems(GenericCommandResultMessage(MessageType(CommandRefusal::class.java), refusal))
        }
    }
}
