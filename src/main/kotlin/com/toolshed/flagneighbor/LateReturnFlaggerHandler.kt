package com.toolshed.flagneighbor

import com.toolshed.axon.ReactionDispatcher
import com.toolshed.axon.ReactionOutcome
import com.toolshed.neighborstoflag.ListNeighborsToFlag
import com.toolshed.neighborstoflag.NeighborsToFlag
import com.toolshed.returntool.ToolReturned
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * The Late Return Flagger. A late return is what puts a neighbor on Neighbors To Flag, so it reads the list then and
 * issues Flag Neighbor for each neighbor on it, naming the loans the list gives. A refusal is logged with the neighbor
 * id and the flagger moves on; no person sees it. Acting twice on the same neighbor is safe (INV-FLG-9).
 */
@Component
class LateReturnFlaggerHandler(
    private val readList: () -> NeighborsToFlag,
    private val send: (Any) -> ReactionOutcome,
) {
    @Autowired
    constructor(queries: QueryGateway, reactions: ReactionDispatcher) : this(
        { queries.query(ListNeighborsToFlag(), NeighborsToFlag::class.java).join() },
        reactions::send,
    )

    @EventHandler
    fun on(event: ToolReturned) {
        if (!event.late) return
        flag(readList())
    }

    /** Issues Flag Neighbor for every neighbor on [list]. */
    fun flag(list: NeighborsToFlag) {
        list.neighbors.forEach { entry ->
            when (val outcome = send(FlagNeighbor(entry.neighborId, entry.loanIds))) {
                is ReactionOutcome.Accepted -> logger.info { "late-return-flagger neighborId=${entry.neighborId.value} outcome=accepted" }
                is ReactionOutcome.Refused ->
                    logger.info {
                        "late-return-flagger neighborId=${entry.neighborId.value} outcome=refused reason=${outcome.reason}"
                    }
            }
        }
    }
}
