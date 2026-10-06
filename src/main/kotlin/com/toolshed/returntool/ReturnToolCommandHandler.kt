package com.toolshed.returntool

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.recordtoollost.ToolLost
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
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

/** A refused return. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class ReturnToolRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/**
 * Return Tool (state change). The decider is pure: the command, the state folded from the loan's
 * checkout, extension, return and loss events, today in the shed's zone, and the moment to record.
 * One criteria over those events makes the decision and its append one boundary, so one loan is
 * decided one at a time.
 */
@Component
class ReturnToolCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: ReturnTool,
        @InjectEntity state: State,
        eventAppender: EventAppender,
    ) {
        val recorded =
            try {
                decide(command, state, LocalDate.now(clock), clock.instant())
            } catch (refused: ReturnToolRejected) {
                logger.info { "return-tool loanId=${command.loanId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        eventAppender.append(recorded)
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "return-tool loanId=${command.loanId.value} outcome=$outcome" }
    }

    /**
     * Pure decision. The checks run in the doc's order; the first that fails is the reason returned.
     * `returnedOn` and `late` are worked out here, once, so every view reads the same answer (INV-RET-4).
     */
    fun decide(
        command: ReturnTool,
        state: State,
        today: LocalDate,
        returnedAt: Instant,
    ): List<ToolReturned> {
        val loan = state.loan(command.loanId) ?: refuse("INV-RET-11", "loan-not-found", "There is no such loan.")
        if (loan.borrower != command.neighborId) {
            refuse("INV-RET-1", "not-your-loan", "You can only return your own loans.")
        }
        // INV-RET-10: returning a loan already returned by this borrower succeeds with nothing recorded.
        if (loan.returned) return emptyList()
        if (loan.lost) refuse("INV-RET-2", "loan-ended", "This loan has ended.")

        // INV-RET-4: late from the start of the day after the due date in force at the moment of return.
        val late = today.isAfter(loan.dueDate)
        return listOf(ToolReturned(command.loanId, loan.toolId, returnedAt, today, late))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw ReturnToolRejected(invariant, reason, sentence)

    /** A loan as far as a return cares: whose it is, what tool, when it is due, and whether it has ended. */
    data class Loan(
        val loanId: LoanId,
        val toolId: ToolId,
        val borrower: NeighborId,
        val dueDate: LocalDate,
        val returned: Boolean,
        val lost: Boolean,
    )

    /**
     * Folded from the checkout, extension, return and loss events. Mutable map, because the shed's
     * history is long and copying the whole state on every event would be wasteful.
     */
    @EventSourced(idType = LoanBook::class)
    class State
        @EntityCreator
        constructor() {
            private val loans = mutableMapOf<LoanId, Loan>()

            fun loan(loanId: LoanId): Loan? = loans[loanId]

            @EventSourcingHandler
            fun evolve(event: ToolCheckedOut) {
                loans[event.loanId] = Loan(event.loanId, event.toolId, event.neighborId, event.dueDate, returned = false, lost = false)
            }

            @EventSourcingHandler
            fun evolve(event: LoanExtended) {
                loans[event.loanId]?.let { loans[event.loanId] = it.copy(dueDate = event.dueDate) }
            }

            @EventSourcingHandler
            fun evolve(event: ToolReturned) {
                loans[event.loanId]?.let { loans[event.loanId] = it.copy(returned = true) }
            }

            @EventSourcingHandler
            fun evolve(event: ToolLost) {
                loans[event.loanId]?.let { loans[event.loanId] = it.copy(lost = true) }
            }

            companion object {
                @EventCriteriaBuilder
                @JvmStatic
                private fun resolveCriteria(book: LoanBook): EventCriteria =
                    EventCriteria
                        .havingAnyTag()
                        .andBeingOneOfTypes(
                            ToolCheckedOut::class.java.name,
                            LoanExtended::class.java.name,
                            ToolReturned::class.java.name,
                            ToolLost::class.java.name,
                        )
            }
        }
}
