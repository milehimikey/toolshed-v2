package com.toolshed.fulfillhold

import com.toolshed.axon.ReactionDispatcher
import com.toolshed.holdstofulfill.HoldsToFulfillRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** The running app's Hold Fulfiller: reads the list in-process from the read slice's repository (Amendment 11). */
@Configuration
class HoldFulfillerConfiguration {
    @Bean
    fun holdFulfiller(
        repository: HoldsToFulfillRepository,
        dispatcher: ReactionDispatcher,
    ): HoldFulfiller =
        HoldFulfiller(
            list = { repository.toolsToFulfill() },
            send = { dispatcher.send(it) },
        )
}
