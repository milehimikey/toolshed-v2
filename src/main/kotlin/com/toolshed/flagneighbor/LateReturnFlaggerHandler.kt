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
    private val attempts: Int = LIST_CATCH_UP_ATTEMPTS,
    private val pause: () -> Unit = { Thread.sleep(LIST_CATCH_UP_PAUSE_MILLIS) },
) {
    @Autowired
    constructor(queries: QueryGateway, reactions: ReactionDispatcher) : this(
        { queries.query(ListNeighborsToFlag(), NeighborsToFlag::class.java).join() },
        reactions::send,
    )

    /**
     * The list is built by its own processor, so it may not have applied this late return yet. Read it until the loan
     * just returned shows on an entry (that neighbor's entry changed), for a bounded time. If it never shows, this
     * late return did not put anyone on the list, or the list is slow; a later late return reads it again.
     */
    @EventHandler
    fun on(event: ToolReturned) {
        if (!event.late) return
        repeat(attempts) { attempt ->
            val changed = readList().neighbors.filter { event.loanId in it.loanIds }
            if (changed.isNotEmpty()) {
                flag(NeighborsToFlag(changed))
                return
            }
            if (attempt < attempts - 1) pause()
        }
        logger.debug { "late-return-flagger loanId=${event.loanId.value} outcome=not-on-list" }
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

private const val LIST_CATCH_UP_ATTEMPTS = 20
private const val LIST_CATCH_UP_PAUSE_MILLIS = 100L
