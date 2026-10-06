package com.toolshed.web

import com.toolshed.axon.CommandRefusal
import com.toolshed.axon.CommandRejected
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.CommandExecutionException
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.core.conversion.MessageConverter
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * The one way a controller sends a command. Results arrive as serialized payloads, so this decodes them with Axon's
 * converter. A refusal is thrown as a CommandRejected, so CommandRejectedAdvice maps it to a 422.
 *
 * A server-side command failure (a CommandExecutionException that is not a refusal) is retried, up to [MAX_ATTEMPTS]
 * attempts in total, each time as a new dispatch. This is safe because every command is idempotent (Amendment 7):
 * a repeat with the same id and details succeeds and records nothing. It is how a race loser re-decides against the
 * winner's events. Refusals are never retried.
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
        var attempt = 1
        while (true) {
            val result =
                try {
                    commandGateway.send(command).getResultMessage().get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS) ?: return null
                } catch (timeout: TimeoutException) {
                    // Not retried: a timed-out command may still have committed, and the caller cannot tell.
                    throw ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Command timed out")
                } catch (failure: ExecutionException) {
                    val cause = failure.cause ?: failure
                    if (attempt >= MAX_ATTEMPTS || cause !is CommandExecutionException) throw failure
                    logger.warn {
                        "command ${command::class.simpleName} failed server-side; retrying, attempt ${attempt + 1} of $MAX_ATTEMPTS"
                    }
                    Thread.sleep(Random.nextLong(BACKOFF_MIN_MILLIS, BACKOFF_MAX_MILLIS))
                    attempt++
                    continue
                } catch (failure: CompletionException) {
                    if (attempt >= MAX_ATTEMPTS || failure.cause !is CommandExecutionException) throw failure
                    logger.warn {
                        "command ${command::class.simpleName} failed server-side; retrying, attempt ${attempt + 1} of $MAX_ATTEMPTS"
                    }
                    Thread.sleep(Random.nextLong(BACKOFF_MIN_MILLIS, BACKOFF_MAX_MILLIS))
                    attempt++
                    continue
                }
            if (result.type().name() == CommandRefusal::class.java.name) {
                val refusal = checkNotNull(result.payloadAs(CommandRefusal::class.java, messageConverter)) { "refusal did not decode" }
                throw CommandRejected(refusal.invariant, refusal.reason, refusal.sentence)
            }
            if (result.payload() == null) return null
            return result.payloadAs(resultType, messageConverter)
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val BACKOFF_MIN_MILLIS = 50L
        const val BACKOFF_MAX_MILLIS = 200L
        const val COMMAND_TIMEOUT_SECONDS = 10L
    }
}
