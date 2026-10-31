package com.toolshed.flagneighbor

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

/** Registers this slice's entity. The id type is UUID: NeighborId is a value class, erased to its UUID at runtime. */
@Configuration
class FlagNeighborWiring {
    @Bean
    fun flagNeighborStateModule(): EventSourcedEntityModule<UUID, FlagState> =
        EventSourcedEntityModule.autodetected(UUID::class.java, FlagState::class.java)
}
