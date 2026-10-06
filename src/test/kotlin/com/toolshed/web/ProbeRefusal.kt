package com.toolshed.web

import com.toolshed.axon.CommandRejected
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/** A command whose handler always refuses, to prove refusals reach the advice over Axon Server. */
data class ProbeRefuse(
    val id: String,
)

/** A command whose normal result is a value, to prove non-refusal results pass the dispatcher unchanged. */
data class ProbeEcho(
    val id: String,
)

class ProbeRejected : CommandRejected("INV-PROBE-1", "probe-refused", "Probe refused.")

@Component
class ProbeHandlers {
    @CommandHandler
    fun refuse(command: ProbeRefuse): Unit = throw ProbeRejected()

    @CommandHandler
    fun echo(command: ProbeEcho): String = "pong"
}

@RestController
class ProbeController(
    private val dispatcher: CommandDispatcher,
) {
    @PostMapping("/probe")
    fun probe(): ResponseEntity<Void> {
        dispatcher.send(ProbeRefuse("x"), Any::class.java)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    @PostMapping("/probe-ok")
    fun probeOk(): ResponseEntity<String> = ResponseEntity.ok(dispatcher.send(ProbeEcho("x"), String::class.java))
}
