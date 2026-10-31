package com.toolshed.flagneighbor

import com.toolshed.axon.ReactionOutcome
import com.toolshed.neighborstoflag.NeighborsToFlag
import com.toolshed.neighborstoflag.NeighborsToFlagReaction
import com.toolshed.returntool.ToolReturned
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * The Late Return Flagger. It is a reaction the Neighbors To Flag projection invokes after each list-changing event's repository
 * write (Amendment 11), so it reads a list that has already applied that event. On each late return it reads the list once and
 * issues Flag Neighbor for every neighbor on it, naming the loans the list gives; acting on the whole list picks up a neighbor
 * left unflagged earlier. It never waits or re-reads. A refusal is logged and left for the next list change;
 * `late-returns-changed` is not retried here. Acting twice on the same neighbor is safe (INV-FLG-9).
 */
class LateReturnFlaggerHandler(
    private val readList: () -> NeighborsToFlag,
    private val send: (Any) -> ReactionOutcome,
) : NeighborsToFlagReaction {
    override fun listChanged(event: Any) {
        if (event !is ToolReturned || !event.late) return
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
