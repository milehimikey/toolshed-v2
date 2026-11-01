package com.toolshed.sendoverduenotice

import com.toolshed.axon.EmailGateway
import com.toolshed.axon.EmailKey
import com.toolshed.axon.HandOffResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter
import java.util.Locale

private val logger = KotlinLogging.logger {}

/** The hand-off was not accepted by the provider. Thrown so the processor repeats the same event, with the same key (INV-OVN-4). */
class OverdueHandOffNotAccepted(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * The outbox: every recorded Overdue Notice Sent is handed to the email provider, keyed by the loan id, from the event itself
 * and not from the list (INV-OVN-4). A refused or erroring hand-off throws, so this processor does not advance past the event
 * and repeats it, with the same key, until the provider accepts. Tokens are in memory (TokenStoreConfiguration), so a restart replays every recorded notice through this hand-off; the provider sends at most one email per key, which is what makes that safe (Amendment 8). The email is built only from what the event recorded (INV-OVN-3, INV-OVN-7).
 */
@Component
class OverdueNoticeOutbox(
    private val emailGateway: EmailGateway,
) {
    @EventHandler
    fun on(event: OverdueNoticeSent) {
        val result =
            try {
                emailGateway.handOff(EmailKey.ForLoan(event.loanId), event.sentTo, subject(event), body(event))
            } catch (error: Exception) {
                logger.info { "overdue-notice-outbox loanId=${event.loanId.value} outcome=error, will repeat" }
                throw OverdueHandOffNotAccepted("overdue hand-off errored", error)
            }
        if (result != HandOffResult.ACCEPTED) {
            logger.info { "overdue-notice-outbox loanId=${event.loanId.value} outcome=not-accepted, will repeat" }
            throw OverdueHandOffNotAccepted("overdue hand-off was not accepted")
        }
        logger.info { "overdue-notice-outbox loanId=${event.loanId.value} outcome=accepted" }
    }

    companion object {
        private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

        fun subject(event: OverdueNoticeSent): String = "Your ${event.toolName} is overdue"

        /** INV-OVN-7: name, tool, due date, a request to return it, and that an overdue loan blocks new checkouts and pickups. */
        fun body(event: OverdueNoticeSent): String {
            val date = event.dueDate.format(DATE)
            return "Hi ${event.borrowerName},\n\n" +
                "The ${event.toolName} you borrowed was due on $date. Please return it as soon as you can.\n\n" +
                "While a loan is overdue, you can't check out new tools or pick up held ones."
        }
    }
}
