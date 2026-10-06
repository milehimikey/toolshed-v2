package com.toolshed.web

import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/** A command that answers too late for the configured timeout. Test-only, behind the probes profile. */
data class ProbeSlow(
    val id: String,
)

@Profile("probes")
@Component
class ProbeSlowHandler {
    @CommandHandler
    fun slow(command: ProbeSlow) {
        Thread.sleep(3_000)
    }
}

@Profile("probes")
@RestController
class ProbeSlowController(
    private val dispatcher: CommandDispatcher,
) {
    @PostMapping("/probe-slow")
    fun slow() {
        dispatcher.send(ProbeSlow("x"), Any::class.java)
    }
}
