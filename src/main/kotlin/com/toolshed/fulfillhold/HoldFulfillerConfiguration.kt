package com.toolshed.fulfillhold

import com.toolshed.axon.ReactionDispatcher
import com.toolshed.holdstofulfill.HoldsToFulfillRow
import com.toolshed.holdstofulfill.ListToolsToFulfill
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Builds the running app's Hold Fulfiller: it reads the list through the query bus and sends through the reaction dispatcher. */
@Configuration
class HoldFulfillerConfiguration {
    @Bean
    fun holdFulfiller(
        queryGateway: QueryGateway,
        dispatcher: ReactionDispatcher,
    ): HoldFulfiller =
        HoldFulfiller(
            list = { queryGateway.queryMany(ListToolsToFulfill, HoldsToFulfillRow::class.java).join() },
            send = { dispatcher.send(it) },
            catchUpDelaysMillis = listOf(100, 300, 900),
        )
}
