package com.toolshed.recordtoollost

import com.toolshed.axon.decideAndAppend
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock

private val logger = KotlinLogging.logger {}

/** Stamps the loss with the clock reading, then decides and appends through the foundation runner. */
@Component
class RecordToolLostCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: RecordToolLost,
        @InjectEntity state: LoanState,
        context: ProcessingContext,
    ) {
        val lostAt = clock.instant()
        val events =
            try {
                context.decideAndAppend { RecordToolLostDecider.decide(command, state, lostAt) }
            } catch (refused: RecordToolLostRejected) {
                logger.info { "record-tool-lost loan=${command.loanId.value} reason=${refused.reason}" }
                throw refused
            }
        val outcome = if (events.isEmpty()) "repeat, nothing recorded" else "recorded"
        logger.info { "record-tool-lost loan=${command.loanId.value} outcome=$outcome" }
    }
}
