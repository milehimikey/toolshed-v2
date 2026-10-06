package com.toolshed.extendloan

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
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

/** A refused extension. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class ExtendLoanRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/**
 * Extend Loan (state change). The decider is pure: the command, the state folded from the shed's
 * loan, hold, suspension and retirement events, today in the shed's zone, and the moment to record.
 * One criteria over every upstream event type makes the decision and its append one boundary, so
 * two extensions from the same due date cannot both be recorded.
 */
@Component
class ExtendLoanCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: ExtendLoan,
        @InjectEntity state: State,
        eventAppender: EventAppender,
    ) {
        val recorded =
            try {
                decide(command, state, LocalDate.now(clock), clock.instant())
            } catch (refused: ExtendLoanRejected) {
                logger.info { "extend-loan loanId=${command.loanId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        eventAppender.append(recorded)
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "extend-loan loanId=${command.loanId.value} outcome=$outcome" }
    }

    /** Pure decision. The checks run in the doc's order; the first that fails is the one returned. */
    fun decide(
        command: ExtendLoan,
        state: State,
        today: LocalDate,
        extendedAt: Instant,
    ): List<LoanExtended> {
        val loan = state.loan(command.loanId) ?: refuse("INV-EXT-9", "loan-not-found", "There is no such loan.")
        if (loan.borrower != command.neighborId) {
            refuse("INV-EXT-1", "not-your-loan", "You can only extend your own loans.")
        }
        // INV-EXT-10: a repeat from a due date already extended from succeeds with nothing recorded.
        if (command.fromDueDate in loan.extendedFrom) return emptyList()
        if (loan.ended) refuse("INV-EXT-2", "loan-ended", "This loan has ended.")
        if (command.fromDueDate != loan.dueDate) {
            refuse("INV-EXT-10", "due-date-changed", "This loan's due date has changed. Check it and try again.")
        }
        if (state.isSuspended(loan.borrower)) {
            refuse("INV-EXT-7", "neighbor-suspended", "You can't extend loans while your account is suspended.")
        }
        if (state.isRetired(loan.toolId)) {
            refuse(
                "INV-EXT-8",
                "tool-retired",
                "This tool has been retired, so this loan can't be extended. Please return it by the due date.",
            )
        }
        if (today.isAfter(loan.dueDate)) {
            refuse("INV-EXT-5", "loan-overdue", "This loan is overdue and can't be extended. Please return the tool.")
        }
        if (state.waitingHoldsOn(loan.toolId) > 0) {
            refuse("INV-EXT-4", "hold-waiting", "Someone is waiting for this tool, so this loan can't be extended.")
        }

        val period = checkNotNull(state.defaultPeriodDays(loan.toolId)) { "no default period for tool ${loan.toolId}" }
        return listOf(
            LoanExtended(
                loanId = loan.loanId,
                fromDueDate = command.fromDueDate,
                dueDate = command.fromDueDate.plusDays(period.toLong()),
                extendedAt = extendedAt,
            ),
        )
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw ExtendLoanRejected(invariant, reason, sentence)

    /**
     * Folded from every upstream event the decision reads. Mutable maps, because the shed's history
     * is long and copying the whole state on every event would be wasteful.
     */
    @EventSourced(idType = LoanLedger::class)
    class State
        @EntityCreator
        constructor() {
            private val loans = mutableMapOf<LoanId, Loan>()
            private val defaultPeriods = mutableMapOf<ToolId, Int>()
            private val retiredTools = mutableSetOf<ToolId>()
            private val suspended = mutableSetOf<NeighborId>()
            private val waitingHolds = mutableMapOf<HoldId, WaitingHold>()

            fun loan(loanId: LoanId): Loan? = loans[loanId]

            fun defaultPeriodDays(toolId: ToolId): Int? = defaultPeriods[toolId]

            fun isRetired(toolId: ToolId): Boolean = toolId in retiredTools

            fun isSuspended(neighborId: NeighborId): Boolean = neighborId in suspended

            /** A hold is waiting from Hold Placed until it is fulfilled, cancelled, expired or otherwise ends. */
            fun waitingHoldsOn(toolId: ToolId): Int = waitingHolds.values.count { it.toolId == toolId }

            @EventSourcingHandler
            fun evolve(event: ToolAdded) {
                defaultPeriods[event.toolId] = event.defaultCheckoutPeriodDays
            }

            @EventSourcingHandler
            fun evolve(event: ToolRetired) {
                retiredTools.add(event.toolId)
                endHoldsOn(event.toolId)
            }

            @EventSourcingHandler
            fun evolve(event: ToolCheckedOut) {
                loans[event.loanId] = Loan(event.loanId, event.toolId, event.neighborId, event.dueDate, emptySet(), false)
                // The holder checking the tool out ends their own waiting holds on it.
                waitingHolds.entries.removeAll { it.value.toolId == event.toolId && it.value.neighborId == event.neighborId }
            }

            @EventSourcingHandler
            fun evolve(event: LoanExtended) {
                val loan = loans[event.loanId] ?: return
                loans[event.loanId] = loan.copy(dueDate = event.dueDate, extendedFrom = loan.extendedFrom + event.fromDueDate)
            }

            @EventSourcingHandler
            fun evolve(event: ToolReturned) {
                endLoan(event.loanId)
            }

            @EventSourcingHandler
            fun evolve(event: ToolLost) {
                endLoan(event.loanId)
                endHoldsOn(event.toolId)
            }

            @EventSourcingHandler
            fun evolve(event: NeighborSuspended) {
                suspended.add(event.neighborId)
            }

            @EventSourcingHandler
            fun evolve(event: NeighborReinstated) {
                suspended.remove(event.neighborId)
            }

            @EventSourcingHandler
            fun evolve(event: HoldPlaced) {
                waitingHolds[event.holdId] = WaitingHold(event.toolId, event.neighborId)
            }

            @EventSourcingHandler
            fun evolve(event: HoldFulfilled) {
                waitingHolds.remove(event.holdId)
            }

            @EventSourcingHandler
            fun evolve(event: HoldCancelled) {
                waitingHolds.remove(event.holdId)
            }

            @EventSourcingHandler
            fun evolve(event: HoldExpired) {
                waitingHolds.remove(event.holdId)
            }

            /** Closing an account ends that neighbor's waiting holds (the holder closes their account). */
            @EventSourcingHandler
            fun evolve(event: NeighborAccountClosed) {
                waitingHolds.entries.removeAll { it.value.neighborId == event.neighborId }
            }

            private fun endLoan(loanId: LoanId) {
                loans[loanId] = loans[loanId]?.copy(ended = true) ?: return
            }

            private fun endHoldsOn(toolId: ToolId) {
                waitingHolds.entries.removeAll { it.value.toolId == toolId }
            }

            companion object {
                @EventCriteriaBuilder
                @JvmStatic
                private fun resolveCriteria(ledger: LoanLedger): EventCriteria =
                    EventCriteria
                        .havingAnyTag()
                        .andBeingOneOfTypes(
                            ToolAdded::class.java.name,
                            ToolRetired::class.java.name,
                            ToolCheckedOut::class.java.name,
                            LoanExtended::class.java.name,
                            ToolReturned::class.java.name,
                            ToolLost::class.java.name,
                            NeighborSuspended::class.java.name,
                            NeighborReinstated::class.java.name,
                            HoldPlaced::class.java.name,
                            HoldFulfilled::class.java.name,
                            HoldCancelled::class.java.name,
                            HoldExpired::class.java.name,
                            NeighborAccountClosed::class.java.name,
                        )
            }
        }

    /** A loan as far as an extension cares: whose it is, what it's for, and when it's due. */
    data class Loan(
        val loanId: LoanId,
        val toolId: ToolId,
        val borrower: NeighborId,
        val dueDate: LocalDate,
        val extendedFrom: Set<LocalDate>,
        val ended: Boolean,
    )

    data class WaitingHold(
        val toolId: ToolId,
        val neighborId: NeighborId,
    )
}
