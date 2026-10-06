package com.toolshed.addtool

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

/**
 * Registers this slice's entity. The id type is UUID: ToolId is a value class, erased to its UUID at runtime,
 * so the framework looks the entity up by UUID.
 */
@Configuration
class AddToolWiring {
    @Bean
    fun toolStateModule(): EventSourcedEntityModule<UUID, ToolState> =
        EventSourcedEntityModule.autodetected(UUID::class.java, ToolState::class.java)
}
