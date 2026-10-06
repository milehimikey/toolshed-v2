package com.toolshed.axon

import org.springframework.stereotype.Component

/** What an internal reaction got back. A refusal is an outcome, not an exception: the processor decides what to do. */
sealed interface ReactionOutcome {
    data object Accepted : ReactionOutcome

    data class Refused(
        val invariant: String,
        val reason: String,
    ) : ReactionOutcome
}

/**
 * THE way automations and translations dispatch a command. A reaction runs inside an event processor, not a request, so it
 * gets the same bounded retry as a controller (server-side failures, up to three attempts; never refusals, never timeouts)
 * and reports a refusal as [ReactionOutcome.Refused]. Slices never inject CommandGateway directly.
 */
@Component
class ReactionDispatcher(
    private val sender: CommandSender,
) {
    fun send(command: Any): ReactionOutcome =
        when (val outcome = sender.send(command, null)) {
            is CommandOutcome.Accepted -> ReactionOutcome.Accepted
            is CommandOutcome.Refused -> ReactionOutcome.Refused(outcome.invariant, outcome.reason)
        }
}
