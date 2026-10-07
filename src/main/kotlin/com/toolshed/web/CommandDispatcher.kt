package com.toolshed.web

import com.toolshed.axon.CommandOutcome
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.CommandSender
import com.toolshed.axon.CommandTimedOutException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * The one way a controller sends a command. A refusal is thrown as a CommandRejected, so CommandRejectedAdvice maps it to a
 * 422. A timeout is a 504. The retry rule lives in CommandSender, shared with internal reactions.
 */
@Component
class CommandDispatcher(
    private val sender: CommandSender,
) {
    @Suppress("UNCHECKED_CAST")
    fun <R : Any> send(
        command: Any,
        resultType: Class<R>,
    ): R? {
        val outcome =
            try {
                sender.send(command, resultType)
            } catch (timeout: CommandTimedOutException) {
                throw ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Command timed out")
            }
        return when (outcome) {
            is CommandOutcome.Accepted -> outcome.result as R?
            is CommandOutcome.Refused -> throw CommandRejected(outcome.invariant, outcome.reason, outcome.sentence)
        }
    }
}
