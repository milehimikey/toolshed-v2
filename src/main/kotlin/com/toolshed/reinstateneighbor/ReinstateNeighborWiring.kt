package com.toolshed.reinstateneighbor

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

/**
 * Registers this slice's entity. The id type is UUID: NeighborId is a value class, erased to its UUID at runtime,
 * so the framework looks the entity up by UUID.
 */
@Configuration
class ReinstateNeighborWiring {
    @Bean
    fun reinstateNeighborStateModule(): EventSourcedEntityModule<UUID, NeighborState> =
        EventSourcedEntityModule.autodetected(UUID::class.java, NeighborState::class.java)
}
