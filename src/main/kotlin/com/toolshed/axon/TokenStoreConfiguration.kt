package com.toolshed.axon

import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.inmemory.InMemoryTokenStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The one token store for every event processor. Read models are in-memory (Amendment 5), so their tokens are
 * in-memory too: a restart starts each processor from a fresh token, which replays the stream from the start and
 * rebuilds every view. A durable store here would make a restarted view come back empty.
 */
@Configuration
class TokenStoreConfiguration {
    @Bean
    fun tokenStore(): TokenStore = InMemoryTokenStore()
}
