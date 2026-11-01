package com.toolshed.sendoverduenotice

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/** One clock reading serves the overdue check and sentAt; then decide and append through the foundation runner. */
@Component
class SendOverdueNoticeCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: SendOverdueNotice,
        @InjectEntity state: SendOverdueNoticeState,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                context.decideAndAppend { SendOverdueNoticeDecider.decide(command, state, clock.instant(), clock.zone) }
            } catch (refused: SendOverdueNoticeRejected) {
                logger.info { "send-overdue-notice loanId=${command.loanId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "send-overdue-notice loanId=${command.loanId.value} outcome=$outcome" }
    }
}
