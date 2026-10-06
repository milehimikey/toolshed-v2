package com.toolshed.axon

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
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

    /** Selects the email provider by `toolshed.email.provider`. Only `logging` exists until a real provider is chosen. */
    @Bean
    fun emailGateway(
        @Value("\${toolshed.email.provider:logging}") provider: String,
    ): EmailGateway = selectEmailGateway(provider)

    internal fun selectEmailGateway(provider: String): EmailGateway =
        when (provider) {
            "logging" -> LoggingEmailGateway()
            else -> throw IllegalArgumentException("unknown toolshed.email.provider '$provider'")
        }
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
