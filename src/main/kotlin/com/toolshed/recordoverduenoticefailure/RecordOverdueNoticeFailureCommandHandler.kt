package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Record Overdue Notice Failure's command handler. It decides first and appends only on success, on the processing
 * context, so a refusal and an append are never both observed. One criteria over the notice, failure, return and
 * loss events makes the decision and its append one boundary.
 */
@Component
class RecordOverdueNoticeFailureCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: RecordOverdueNoticeFailure,
        @InjectEntity state: NoticeState,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                context.decideAndAppend { RecordOverdueNoticeFailureDecider.decide(command, state, clock.instant()) }
            } catch (refused: RecordOverdueNoticeFailureRejected) {
                logger.info { "overdue-notice-failure loanId=${command.loanId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "overdue-notice-failure loanId=${command.loanId.value} outcome=$outcome" }
    }
}
