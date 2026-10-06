package com.toolshed.extendloan

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

/**
 * Extend Loan's command handler. It loads the state through one criteria over every upstream event type,
 * and appends the decider's events on the processing context, so the decision and its append are one boundary:
 * two extensions from the same due date cannot both be recorded.
 */
@Component
class ExtendLoanCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: ExtendLoan,
        @InjectEntity state: LoanLedgerState,
        context: ProcessingContext,
    ) {
        val now = clock.instant()
        val today: LocalDate = now.atZone(clock.zone).toLocalDate()
        val recorded =
            try {
                context.decideAndAppend { ExtendLoanDecider.decide(command, state, today, now) }
            } catch (refused: ExtendLoanRejected) {
                logger.info { "extend-loan loanId=${command.loanId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "extend-loan loanId=${command.loanId.value} outcome=$outcome" }
    }
}
