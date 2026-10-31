package com.toolshed.flagneighbor

import com.toolshed.axon.ReactionDispatcher
import com.toolshed.axon.ReactionOutcome
import com.toolshed.neighborstoflag.ListNeighborsToFlag
import com.toolshed.neighborstoflag.NeighborsToFlag
import com.toolshed.neighborstoflag.NeighborsToFlagReaction
import com.toolshed.returntool.ToolReturned
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * The Late Return Flagger. It runs on Neighbors To Flag's own processor, after the projection (Amendment 11), so the list has
 * already applied the late return when this reads it. On each late return it reads the list once and issues Flag Neighbor for
 * every neighbor on it, naming the loans the list gives; acting on the whole list picks up a neighbor left unflagged earlier.
 * It never waits or re-reads. A refusal is logged and left for the next list change; `late-returns-changed` is not retried
 * here. Acting twice on the same neighbor is safe (INV-FLG-9).
 */
@Component
class LateReturnFlaggerHandler(
    private val readList: () -> NeighborsToFlag,
    private val send: (Any) -> ReactionOutcome,
) : NeighborsToFlagReaction {
    @Autowired
    constructor(queries: QueryGateway, reactions: ReactionDispatcher) : this(
        { queries.query(ListNeighborsToFlag(), NeighborsToFlag::class.java).join() },
        reactions::send,
    )

    @EventHandler
    fun on(event: ToolReturned) {
        if (!event.late) return
        val list = readList()
        if (list.neighbors.none { event.loanId in it.loanIds }) {
            logger.info { "late-return-flagger loanId=${event.loanId.value} outcome=not-on-list" }
        }
        list.neighbors.forEach { entry ->
            when (val outcome = send(FlagNeighbor(entry.neighborId, entry.loanIds))) {
                is ReactionOutcome.Accepted ->
                    logger.info { "late-return-flagger neighborId=${entry.neighborId.value} outcome=accepted" }
                is ReactionOutcome.Refused ->
                    logger.info {
                        "late-return-flagger neighborId=${entry.neighborId.value} outcome=refused reason=${outcome.reason}"
                    }
            }
        }
    }
}
