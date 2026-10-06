package com.toolshed.cancelhold

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
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

/** A refused cancellation. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class CancelHoldRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)

/**
 * Cancel Hold (state change). The decider is pure: the command, the state folded from the holds'
 * placement, fulfilment, expiry, checkout, retirement, loss and closure events, and the moment to
 * record. One criteria over those event types makes the decision and its append one boundary.
 */
@Component
class CancelHoldCommandHandler(
    private val clock: Clock,
) {
    @CommandHandler
    fun handle(
        command: CancelHold,
        @InjectEntity state: State,
        eventAppender: EventAppender,
    ) {
        val recorded =
            try {
                decide(command, state, clock.instant())
            } catch (refused: CancelHoldRejected) {
                logger.info { "cancel-hold holdId=${command.holdId.value} outcome=refused reason=${refused.reason}" }
                throw refused
            }
        eventAppender.append(recorded)
        val outcome = if (recorded.isEmpty()) "repeat" else "recorded"
        logger.info { "cancel-hold holdId=${command.holdId.value} outcome=$outcome" }
    }

    /** Pure decision. The checks run in the doc's order; the first that fails is the reason returned. */
    fun decide(
        command: CancelHold,
        state: State,
        cancelledAt: Instant,
    ): List<HoldCancelled> {
        val hold = state.hold(command.holdId) ?: refuse("INV-CNH-7", "hold-not-found", "There is no such hold.")
        if (hold.holder != command.neighborId) {
            refuse("INV-CNH-1", "not-your-hold", "You can only cancel your own holds.")
        }
        // INV-CNH-8: cancelling a hold already cancelled succeeds and records nothing.
        if (hold.status == Status.CANCELLED) return emptyList()
        if (hold.status == Status.ENDED) refuse("INV-CNH-3", "hold-ended", "This hold has already ended.")

        return listOf(HoldCancelled(command.holdId, cancelledAt))
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw CancelHoldRejected(invariant, reason, sentence)

    /** Where a hold stands. WAITING and SET_ASIDE are live; the other two are final. */
    enum class Status { WAITING, SET_ASIDE, CANCELLED, ENDED }

    /** A hold as far as a cancellation cares: which tool, whose, and where it stands. */
    data class Hold(
        val holdId: HoldId,
        val toolId: ToolId,
        val holder: NeighborId,
        val status: Status,
    )

    /**
     * Folded from every event that starts or ends a hold. Mutable maps, because the shed's history
     * is long and copying the whole state on every event would be wasteful.
     */
    @EventSourced(idType = HoldLedger::class)
    class State
        @EntityCreator
        constructor() {
            private val holds = mutableMapOf<HoldId, Hold>()

            fun hold(holdId: HoldId): Hold? = holds[holdId]

            @EventSourcingHandler
            fun evolve(event: HoldPlaced) {
                holds[event.holdId] = Hold(event.holdId, event.toolId, event.neighborId, Status.WAITING)
            }

            @EventSourcingHandler
            fun evolve(event: HoldFulfilled) {
                replaceIfLive(event.holdId) { it.copy(status = Status.SET_ASIDE) }
            }

            @EventSourcingHandler
            fun evolve(event: HoldCancelled) {
                replaceIfLive(event.holdId) { it.copy(status = Status.CANCELLED) }
            }

            @EventSourcingHandler
            fun evolve(event: HoldExpired) {
                replaceIfLive(event.holdId) { it.copy(status = Status.ENDED) }
            }

            /** The holder checking the tool out ends their own hold on it. */
            @EventSourcingHandler
            fun evolve(event: ToolCheckedOut) {
                endLive { it.toolId == event.toolId && it.holder == event.neighborId }
            }

            @EventSourcingHandler
            fun evolve(event: ToolRetired) {
                endLive { it.toolId == event.toolId }
            }

            @EventSourcingHandler
            fun evolve(event: ToolLost) {
                endLive { it.toolId == event.toolId }
            }

            @EventSourcingHandler
            fun evolve(event: NeighborAccountClosed) {
                endLive { it.holder == event.neighborId }
            }

            private fun replaceIfLive(
                holdId: HoldId,
                change: (Hold) -> Hold,
            ) {
                val hold = holds[holdId] ?: return
                if (hold.status == Status.WAITING || hold.status == Status.SET_ASIDE) holds[holdId] = change(hold)
            }

            private fun endLive(matches: (Hold) -> Boolean) {
                holds.replaceAll { _, hold ->
                    if ((hold.status == Status.WAITING || hold.status == Status.SET_ASIDE) && matches(hold)) {
                        hold.copy(status = Status.ENDED)
                    } else {
                        hold
                    }
                }
            }

            companion object {
                @EventCriteriaBuilder
                @JvmStatic
                private fun resolveCriteria(ledger: HoldLedger): EventCriteria =
                    EventCriteria
                        .havingAnyTag()
                        .andBeingOneOfTypes(
                            HoldPlaced::class.java.name,
                            HoldFulfilled::class.java.name,
                            HoldCancelled::class.java.name,
                            HoldExpired::class.java.name,
                            ToolCheckedOut::class.java.name,
                            ToolRetired::class.java.name,
                            ToolLost::class.java.name,
                            NeighborAccountClosed::class.java.name,
                        )
            }
        }
}
