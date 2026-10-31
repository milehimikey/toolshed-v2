package com.toolshed.sendpickupnotice

import com.toolshed.axon.EmailGateway
import com.toolshed.axon.EmailKey
import com.toolshed.axon.HandOffResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter
import java.util.Locale

private val logger = KotlinLogging.logger {}

/** The hand-off was not accepted by the provider. Thrown so the processor repeats the same event, with the same key (INV-PKN-4). */
class PickupHandOffNotAccepted(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * The outbox: every recorded Pickup Notice Sent is handed to the email provider, keyed by the hold id, from the event itself
 * and not from the list (INV-PKN-4). A refused or erroring hand-off throws, so this processor does not advance past the event
 * and repeats it, with the same key, until the provider accepts; its token carries that across a restart. The provider sends
 * at most one email per key (Amendment 8). The email is built only from what the event recorded (INV-PKN-3, INV-PKN-7).
 */
@Component
class PickupNoticeOutbox(
    private val emailGateway: EmailGateway,
) {
    @EventHandler
    fun on(event: PickupNoticeSent) {
        val result =
            try {
                emailGateway.handOff(EmailKey.ForHold(event.holdId), event.sentTo, subject(event), body(event))
            } catch (error: Exception) {
                logger.info { "pickup-notice-outbox holdId=${event.holdId.value} outcome=error, will repeat" }
                throw PickupHandOffNotAccepted("pickup hand-off errored", error)
            }
        if (result != HandOffResult.ACCEPTED) {
            logger.info { "pickup-notice-outbox holdId=${event.holdId.value} outcome=not-accepted, will repeat" }
            throw PickupHandOffNotAccepted("pickup hand-off was not accepted")
        }
        logger.info { "pickup-notice-outbox holdId=${event.holdId.value} outcome=accepted" }
    }

    companion object {
        private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

        fun subject(event: PickupNoticeSent): String = "Your ${event.toolName} is ready for pickup"

        /** INV-PKN-7: the holder's name, the tool's name, the collect-by date, and what happens if it isn't collected. */
        fun body(event: PickupNoticeSent): String {
            val date = event.collectByDate.format(DATE)
            return "Hi ${event.holderName},\n\n" +
                "The ${event.toolName} you asked for is ready for you. Please collect it by $date.\n\n" +
                "If it isn't collected by then, your hold expires and the ${event.toolName} passes to the next neighbor."
        }
    }
}
