package com.toolshed.axon.tokenprobe

import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A probe projection: it records every ProbeEvent it is handed, so a test can see what a context rebuilt. */
data class ProbeEvent(
    val id: UUID,
)

@Component
class TokenProbe {
    val seen: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @EventHandler
    fun on(event: ProbeEvent) {
        seen.add(event.id)
    }
}
