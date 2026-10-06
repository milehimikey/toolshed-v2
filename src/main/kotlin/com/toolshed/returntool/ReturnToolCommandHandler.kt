package com.toolshed.returntool

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Return Tool's command handler. It loads the state through one criteria over the loan events and appends the
 * decider's events on the processing context, so one loan is decided one at a time. The clock is read once, so
 * returnedAt and returnedOn always agree.
 */
@Component
class ReturnToolCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: ReturnTool,
        @InjectEntity state: LoanBookState,
        context: ProcessingContext,
    ) {
        val recorded =
            try {
                // One reading of the clock, so returnedAt and returnedOn always agree (the shed's local date of returnedAt).
                val now = clock.instant()
                context.decideAndAppend { ReturnToolDecider.decide(command, state, now.atZone(clock.zone).toLocalDate(), now) }
            } catch (refused: ReturnToolRejected) {
                logger.info { "return-tool loanId=${command.loanId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "return-tool loanId=${command.loanId.value} outcome=$outcome" }
    }
}
