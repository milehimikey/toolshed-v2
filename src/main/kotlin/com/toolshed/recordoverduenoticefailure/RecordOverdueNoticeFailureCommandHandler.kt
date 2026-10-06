package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.LoanId
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.returntool.ToolReturned
import com.toolshed.sendoverduenotice.OverdueNoticeSent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

private val logger = KotlinLogging.logger {}

/** A refused failure report. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class RecordOverdueNoticeFailureRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/**
 * Record Overdue Notice Failure (translation's command). Decide first, append only on success, so
 * the refusal and the append are never both observed. One criteria over the notice, failure and loan
 * events makes the decision and its append one boundary.
 */
@Component
class RecordOverdueNoticeFailureCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: RecordOverdueNoticeFailure,
        @InjectEntity state: State,
        eventAppender: EventAppender,
    ) {
        val recorded =
            try {
                decide(command, state, clock.instant())
            } catch (refused: RecordOverdueNoticeFailureRejected) {
                logger.info { "overdue-notice-failure loanId=${command.loanId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        eventAppender.append(recorded)
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "overdue-notice-failure loanId=${command.loanId.value} outcome=$outcome" }
    }

    /** Pure decision. The checks run in the doc's order; the first that fails is the reason returned. */
    fun decide(
        command: RecordOverdueNoticeFailure,
        state: State,
        failedAt: Instant,
    ): List<OverdueNoticeFailed> {
        if (!state.wasNoticeSent(command.loanId)) {
            refuse("INV-ONF-7", "no-matching-notice", "No overdue notice was sent for that loan.")
        }
        // INV-ONF-9: a repeat with the same reason succeeds with nothing recorded.
        state.failureReason(command.loanId)?.let { prior ->
            if (prior == command.reason) return emptyList()
            refuse("INV-ONF-9", "failure-already-recorded", "A failure is already recorded for that notice.")
        }
        if (state.hasEnded(command.loanId)) refuse("INV-ONF-8", "loan-ended", "That loan has already ended.")

        return listOf(OverdueNoticeFailed(command.loanId, command.reason, failedAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw RecordOverdueNoticeFailureRejected(invariant, reason, sentence)

    /**
     * Folded from the notice, failure, return and loss events. Mutable collections, because the
     * shed's history is long and copying the whole state on every event would be wasteful.
     */
    @EventSourced(idType = NoticeLedger::class)
    class State
        @EntityCreator
        constructor() {
            private val sent = mutableSetOf<LoanId>()
            private val failures = mutableMapOf<LoanId, String>()
            private val ended = mutableSetOf<LoanId>()

            fun wasNoticeSent(loanId: LoanId): Boolean = loanId in sent

            fun failureReason(loanId: LoanId): String? = failures[loanId]

            fun hasEnded(loanId: LoanId): Boolean = loanId in ended

            @EventSourcingHandler
            fun evolve(event: OverdueNoticeSent) {
                sent.add(event.loanId)
            }

            @EventSourcingHandler
            fun evolve(event: OverdueNoticeFailed) {
                failures[event.loanId] = event.reason
            }

            @EventSourcingHandler
            fun evolve(event: ToolReturned) {
                ended.add(event.loanId)
            }

            @EventSourcingHandler
            fun evolve(event: ToolLost) {
                ended.add(event.loanId)
            }

            companion object {
                @EventCriteriaBuilder
                @JvmStatic
                private fun resolveCriteria(ledger: NoticeLedger): EventCriteria =
                    EventCriteria
                        .havingAnyTag()
                        .andBeingOneOfTypes(
                            OverdueNoticeSent::class.java.name,
                            OverdueNoticeFailed::class.java.name,
                            ToolReturned::class.java.name,
                            ToolLost::class.java.name,
                        )
            }
        }
}
