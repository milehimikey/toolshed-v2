package com.toolshed.recordpickupnoticefailure

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.EmailKey
import com.toolshed.axon.FailureReport
import com.toolshed.axon.FailureReportVerdict
import com.toolshed.axon.FailureReportVerifier
import com.toolshed.web.CommandDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * The email provider's pickup failure report, turned into [RecordPickupNoticeFailure] or dropped. The foundation
 * verifier checks the signature first; an unverified report builds no command. There is no inbound endpoint: the
 * report arrives from the provider, which is not chosen yet, so this waits for that provider.
 */
@Component
class PickupFailureReportTranslation(
    private val verifier: FailureReportVerifier,
    private val dispatcher: CommandDispatcher,
) {
    fun receive(report: FailureReport) {
        val command = commandFor(verifier.verify(report))
        if (command == null) {
            logger.info { "pickup-notice-failure report dropped" }
            return
        }
        try {
            dispatcher.send(command, Any::class.java)
        } catch (refused: CommandRejected) {
            // A refusal takes no further action; it is logged for diagnosing the provider link.
            logger.info { "pickup-notice-failure refused invariant=${refused.invariant} reason=${refused.reason}" }
        }
    }

    companion object {
        /** The command for a verified report about a hold and a legal reason, or null: the report is dropped. */
        fun commandFor(verdict: FailureReportVerdict): RecordPickupNoticeFailure? {
            val verified = verdict as? FailureReportVerdict.Verified ?: return null
            val key = verified.key as? EmailKey.ForHold ?: return null
            if (verified.reason !in RecordPickupNoticeFailure.FAILURE_REASONS) return null
            return RecordPickupNoticeFailure(key.holdId, verified.reason)
        }
    }
}
