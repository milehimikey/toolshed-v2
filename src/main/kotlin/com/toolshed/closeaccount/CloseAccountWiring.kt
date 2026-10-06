package com.toolshed.closeaccount

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

/**
 * Registers this slice's entity. The id type is UUID: NeighborId is a value class, erased to its UUID at runtime,
 * so the framework looks the entity up by UUID.
 */
@Configuration
class CloseAccountWiring {
    @Bean
    fun closeAccountStateModule(): EventSourcedEntityModule<UUID, NeighborAccountState> =
        EventSourcedEntityModule.autodetected(UUID::class.java, NeighborAccountState::class.java)
}
