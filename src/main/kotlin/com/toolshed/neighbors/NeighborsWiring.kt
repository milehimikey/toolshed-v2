package com.toolshed.neighbors

import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.inmemory.InMemoryTokenStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The Neighbors projection's event processor needs a token store. Amendment 5 keeps read models in memory, so the position
 * is in memory too: the view rebuilds from the start of the store on each start, which is what the in-memory view needs.
 */
@Configuration
class NeighborsWiring {
    @Bean
    fun tokenStore(): TokenStore = InMemoryTokenStore()
}
