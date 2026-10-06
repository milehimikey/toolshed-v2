package com.toolshed.axon

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

@Configuration
class SharedPorts {
    @Bean
    fun shedZoneId(
        @Value("\${toolshed.timezone:America/Denver}") zone: String,
    ): ZoneId = ZoneId.of(zone)

    @Bean
    fun clock(zoneId: ZoneId): Clock = Clock.system(zoneId)

    @Bean
    @ConditionalOnMissingBean(EmailGateway::class)
    fun emailGateway(): EmailGateway = LoggingEmailGateway()
}

/** Stand-in until a provider is chosen. Logs keys only, never addresses or bodies. */
class LoggingEmailGateway : EmailGateway {
    override fun handOff(
        key: EmailKey,
        to: String,
        subject: String,
        body: String,
    ): HandOffResult {
        logger.debug { "email hand-off accepted by logging stub, key=$key" }
        return HandOffResult.ACCEPTED
    }
}
