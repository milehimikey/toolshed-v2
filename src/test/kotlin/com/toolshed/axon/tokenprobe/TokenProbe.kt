package com.toolshed.axon.tokenprobe

import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A probe projection event: it carries an id so a test can tell which events were applied, and in what order. */
data class ProbeEvent(
    val id: UUID,
)

/** Records every ProbeEvent it is handed: the set of ids, and the order they arrived in. */
@Component
@Profile("probes")
class TokenProbe {
    val seen: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val order: MutableList<UUID> = CopyOnWriteArrayList()

    @EventHandler
    fun on(event: ProbeEvent) {
        seen.add(event.id)
        order.add(event.id)
    }
}
