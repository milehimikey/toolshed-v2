package com.toolshed.placehold

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
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
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

private val logger = KotlinLogging.logger {}

/** A refused hold. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class PlaceHoldRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/**
 * Place Hold (state change). The decider is pure: the command, the state folded from the shed's
 * tool, loan, hold and account events, and the moment to record. One criteria over every upstream
 * type makes the decision and its append one boundary, so holds on a tool are decided one at a time.
 */
@Component
class PlaceHoldCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: PlaceHold,
        @InjectEntity state: State,
        eventAppender: EventAppender,
    ) {
        val recorded =
            try {
                decide(command, state, clock.instant())
            } catch (refused: PlaceHoldRejected) {
                logger.info { "place-hold holdId=${command.holdId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        eventAppender.append(recorded)
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "place-hold holdId=${command.holdId.value} outcome=$outcome" }
    }

    /** Pure decision. The checks run in the doc's order; the first that fails is the reason returned. */
    fun decide(
        command: PlaceHold,
        state: State,
        placedAt: Instant,
    ): List<HoldPlaced> {
        // INV-HOLD-13: a repeat with the same tool and neighbor succeeds with nothing recorded.
        state.placement(command.holdId)?.let { prior ->
            if (prior.toolId == command.toolId && prior.neighborId == command.neighborId) return emptyList()
            refuse("INV-HOLD-13", "hold-id-in-use", "That hold ID is already used by another hold.")
        }
        if (!state.hasTool(command.toolId)) refuse("INV-HOLD-14", "tool-not-found", "There is no such tool.")
        if (!state.isRegistered(command.neighborId)) {
            refuse("INV-HOLD-14", "neighbor-not-found", "There is no such neighbor.")
        }
        if (state.isRetired(command.toolId)) refuse("INV-HOLD-3", "tool-retired", "This tool has been retired.")
        if (state.isClosed(command.neighborId)) refuse("INV-HOLD-4", "account-closed", "This account is closed.")
        if (state.isSuspended(command.neighborId)) {
            refuse("INV-HOLD-9", "neighbor-suspended", "You can't place holds while your account is suspended.")
        }
        if (state.hasOpenLoan(command.toolId, command.neighborId)) {
            refuse("INV-HOLD-8", "tool-on-loan-to-you", "You have this tool on loan.")
        }
        if (state.hasActiveHold(command.toolId, command.neighborId)) {
            refuse("INV-HOLD-7", "already-holding", "You already have a hold on this tool.")
        }
        if (state.isAvailable(command.toolId)) {
            refuse("INV-HOLD-2", "tool-available", "This tool is available. Check it out instead.")
        }

        return listOf(HoldPlaced(command.holdId, command.toolId, command.neighborId, placedAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw PlaceHoldRejected(invariant, reason, sentence)

    /**
     * Folded from every upstream event the decision reads. Mutable maps, because the shed's history
     * is long and copying the whole state on every event would be wasteful.
     */
    @EventSourced(idType = HoldLedger::class)
    class State
        @EntityCreator
        constructor() {
            private val tools = mutableSetOf<ToolId>()
            private val retired = mutableSetOf<ToolId>()
            private val registered = mutableSetOf<NeighborId>()
            private val closed = mutableSetOf<NeighborId>()
            private val suspended = mutableSetOf<NeighborId>()
            private val openLoans = mutableMapOf<LoanId, OpenLoan>()
            private val holds = mutableMapOf<HoldId, Hold>()
            private val placements = mutableMapOf<HoldId, Placement>()

            fun placement(holdId: HoldId): Placement? = placements[holdId]

            fun hasTool(toolId: ToolId): Boolean = toolId in tools

            fun isRegistered(neighborId: NeighborId): Boolean = neighborId in registered

            fun isRetired(toolId: ToolId): Boolean = toolId in retired

            fun isClosed(neighborId: NeighborId): Boolean = neighborId in closed

            fun isSuspended(neighborId: NeighborId): Boolean = neighborId in suspended

            fun hasOpenLoan(
                toolId: ToolId,
                neighborId: NeighborId,
            ): Boolean = openLoans.values.any { it.toolId == toolId && it.borrower == neighborId }

            fun hasActiveHold(
                toolId: ToolId,
                neighborId: NeighborId,
            ): Boolean = holds.values.any { it.toolId == toolId && it.neighborId == neighborId }

            /** Checked out, then set aside, then reserved; available only when none of those holds (INV-HOLD-2). */
            fun isAvailable(toolId: ToolId): Boolean =
                openLoans.values.none { it.toolId == toolId } &&
                    holds.values.none { it.toolId == toolId }

            @EventSourcingHandler
            fun evolve(event: ToolAdded) {
                tools.add(event.toolId)
            }

            @EventSourcingHandler
            fun evolve(event: ToolRetired) {
                retired.add(event.toolId)
                endHoldsOn(event.toolId)
            }

            @EventSourcingHandler
            fun evolve(event: ToolLost) {
                retired.add(event.toolId)
                endLoan(event.loanId)
                endHoldsOn(event.toolId)
            }

            @EventSourcingHandler
            fun evolve(event: ToolCheckedOut) {
                openLoans[event.loanId] = OpenLoan(event.toolId, event.neighborId)
                // The holder checking the tool out ends their own hold on it, waiting or set aside.
                holds.entries.removeAll { it.value.toolId == event.toolId && it.value.neighborId == event.neighborId }
            }

            @EventSourcingHandler
            fun evolve(event: ToolReturned) {
                endLoan(event.loanId)
            }

            @EventSourcingHandler
            fun evolve(event: NeighborRegistered) {
                registered.add(event.neighborId)
            }

            @EventSourcingHandler
            fun evolve(event: NeighborSuspended) {
                suspended.add(event.neighborId)
            }

            @EventSourcingHandler
            fun evolve(event: NeighborReinstated) {
                suspended.remove(event.neighborId)
            }

            /** Closing ends all of the neighbor's holds. Closed is final, and what shows (N5). */
            @EventSourcingHandler
            fun evolve(event: NeighborAccountClosed) {
                closed.add(event.neighborId)
                holds.entries.removeAll { it.value.neighborId == event.neighborId }
            }

            @EventSourcingHandler
            fun evolve(event: HoldPlaced) {
                placements[event.holdId] = Placement(event.toolId, event.neighborId)
                holds[event.holdId] = Hold(event.toolId, event.neighborId, setAside = false)
            }

            @EventSourcingHandler
            fun evolve(event: HoldFulfilled) {
                holds[event.holdId]?.let { holds[event.holdId] = it.copy(setAside = true) }
            }

            @EventSourcingHandler
            fun evolve(event: HoldCancelled) {
                holds.remove(event.holdId)
            }

            @EventSourcingHandler
            fun evolve(event: HoldExpired) {
                holds.remove(event.holdId)
            }

            private fun endLoan(loanId: LoanId) {
                openLoans.remove(loanId)
            }

            private fun endHoldsOn(toolId: ToolId) {
                holds.entries.removeAll { it.value.toolId == toolId }
            }

            companion object {
                @EventCriteriaBuilder
                @JvmStatic
                private fun resolveCriteria(ledger: HoldLedger): EventCriteria =
                    EventCriteria
                        .havingAnyTag()
                        .andBeingOneOfTypes(
                            ToolAdded::class.java.name,
                            ToolRetired::class.java.name,
                            ToolLost::class.java.name,
                            ToolCheckedOut::class.java.name,
                            ToolReturned::class.java.name,
                            HoldPlaced::class.java.name,
                            HoldFulfilled::class.java.name,
                            HoldCancelled::class.java.name,
                            HoldExpired::class.java.name,
                            NeighborRegistered::class.java.name,
                            NeighborSuspended::class.java.name,
                            NeighborReinstated::class.java.name,
                            NeighborAccountClosed::class.java.name,
                        )
            }
        }

    /** What a hold was placed for. Kept after the hold ends, so a repeat is still recognised. */
    data class Placement(
        val toolId: ToolId,
        val neighborId: NeighborId,
    )

    /** A hold that is still standing: waiting in the queue, or set aside for its holder. */
    data class Hold(
        val toolId: ToolId,
        val neighborId: NeighborId,
        val setAside: Boolean,
    )

    data class OpenLoan(
        val toolId: ToolId,
        val borrower: NeighborId,
    )
}
