package com.toolshed.axon

import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.CommandExecutionException
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.core.Message
import org.axonframework.messaging.core.conversion.MessageConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/** What a dispatch came back with: the decoded result, or the refusal the handler gave. */
sealed interface CommandOutcome {
    data class Accepted(
        val result: Any?,
    ) : CommandOutcome

    data class Refused(
        val invariant: String,
        val reason: String,
        val sentence: String,
    ) : CommandOutcome
}

/** The command did not answer in time. Not retried: it may still have committed, and the sender cannot tell. */
class CommandTimedOutException : RuntimeException("command timed out")

/**
 * The one dispatch core. Controllers reach it through CommandDispatcher, internal reactions through ReactionDispatcher.
 *
 * A server-side command failure (a CommandExecutionException that is not a refusal) is retried, up to [MAX_ATTEMPTS]
 * attempts in total, each time as a new dispatch. This is safe because every command is idempotent (Amendment 7): a
 * repeat with the same id and details succeeds and records nothing. It is how a race loser re-decides against the
 * winner's events. A refusal is never retried, and a timeout is never retried.
 */
@Component
class CommandSender(
    private val commandGateway: CommandGateway,
    private val messageConverter: MessageConverter,
    @Value("\${toolshed.command.timeout-seconds:10}") private val timeoutSeconds: Long,
) {
    fun send(
        command: Any,
        resultType: Class<*>?,
    ): CommandOutcome {
        var attempt = 1
        while (true) {
            val result: Message? =
                try {
                    commandGateway.send(command).getResultMessage().get(timeoutSeconds, TimeUnit.SECONDS)
                } catch (timeout: TimeoutException) {
                    throw CommandTimedOutException()
                } catch (failure: ExecutionException) {
                    val cause = failure.cause ?: failure
                    if (attempt >= MAX_ATTEMPTS || cause !is CommandExecutionException) throw failure
                    logger.warn {
                        "command ${command::class.simpleName} failed server-side; retrying, attempt ${attempt + 1} of $MAX_ATTEMPTS"
                    }
                    Thread.sleep(Random.nextLong(BACKOFF_MIN_MILLIS, BACKOFF_MAX_MILLIS))
                    attempt++
                    continue
                }
            return decode(result, resultType)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun decode(
        result: Message?,
        resultType: Class<*>?,
    ): CommandOutcome {
        if (result == null) return CommandOutcome.Accepted(null)
        if (result.type().name() == CommandRefusal::class.java.name) {
            val refusal = checkNotNull(result.payloadAs(CommandRefusal::class.java, messageConverter)) { "refusal did not decode" }
            return CommandOutcome.Refused(refusal.invariant, refusal.reason, refusal.sentence)
        }
        if (resultType == null || result.payload() == null) return CommandOutcome.Accepted(null)
        return CommandOutcome.Accepted(result.payloadAs(resultType as Class<Any>, messageConverter))
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val BACKOFF_MIN_MILLIS = 50L
        const val BACKOFF_MAX_MILLIS = 200L
    }
}
