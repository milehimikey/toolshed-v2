package com.toolshed.expirehold

import com.toolshed.axon.ReactionDispatcher
import com.toolshed.pickupstoexpire.ListPickupsToExpireQueryHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/** The running app's Pickup Expirer: reads Pickups To Expire in-process, through the read slice's query handler. */
@Configuration
@EnableScheduling
class PickupExpirerConfiguration {
    @Bean
    fun pickupExpirer(
        list: ListPickupsToExpireQueryHandler,
        dispatcher: ReactionDispatcher,
    ): PickupExpirer =
        PickupExpirer(
            list = { list.handle(com.toolshed.pickupstoexpire.ListPickupsToExpire) },
            send = { dispatcher.send(it) },
        )
}

/** The timer: every 5 minutes, at any hour (Amendment 7). A failed run is logged and the next one still happens. */
@Component
class PickupExpirerSchedule(
    private val expirer: PickupExpirer,
) {
    @Scheduled(fixedDelayString = "\${toolshed.pickup-expirer.interval:PT5M}")
    fun tick() {
        try {
            expirer.run()
        } catch (failure: RuntimeException) {
            logger.warn(failure) { "pickup-expirer run failed" }
        }
    }
}
