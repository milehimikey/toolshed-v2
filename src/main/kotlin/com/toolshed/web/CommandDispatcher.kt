package com.toolshed.web

import com.toolshed.axon.CommandRefusal
import com.toolshed.axon.CommandRejected
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.core.conversion.MessageConverter
import org.springframework.stereotype.Component

/**
 * The one way a controller sends a command. Results arrive as serialized payloads, so this decodes them with
 * Axon's converter. A refusal is thrown here as a CommandRejected, so CommandRejectedAdvice maps it to a 422.
 */
@Component
class CommandDispatcher(
    private val commandGateway: CommandGateway,
    private val messageConverter: MessageConverter,
) {
    fun <R : Any> send(
        command: Any,
        resultType: Class<R>,
    ): R? {
        val result = commandGateway.send(command).getResultMessage().join()
        if (result.type().name() == CommandRefusal::class.java.name) {
            val refusal = checkNotNull(result.payloadAs(CommandRefusal::class.java, messageConverter)) { "refusal did not decode" }
            throw CommandRejected(refusal.invariant, refusal.reason, refusal.sentence)
        }
        return result.payloadAs(resultType, messageConverter)
    }
}
