package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.CommandRejected
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * The Email Provider Failure Report translation. It turns a report into the command, or drops it.
 * A refusal from the command takes no further action; it is logged for diagnosing the provider link.
 */
@Component
class OverdueNoticeFailureTranslation(
    private val verifier: ProviderReportVerifier,
    private val commandGateway: CommandGateway,
) {
    fun receive(report: ProviderFailureReport) {
        when (val interpretation = ProviderFailureReport.interpret(report, verifier)) {
            is ProviderFailureReport.Interpretation.Dropped -> {
                logger.info { "overdue-notice-failure report dropped why=${interpretation.why}" }
            }
            is ProviderFailureReport.Interpretation.Dispatch -> dispatch(interpretation.command)
        }
    }

    private fun dispatch(command: RecordOverdueNoticeFailure) {
        try {
            commandGateway.sendAndWait(command)
        } catch (ex: RuntimeException) {
            val refused =
                generateSequence<Throwable>(ex) { it.cause }
                    .filterIsInstance<CommandRejected>()
                    .firstOrNull() ?: throw ex
            logger.info { "overdue-notice-failure refused invariant=${refused.invariant} reason=${refused.reason}" }
        }
    }
}
