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
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * The Late Return Flagger. It acts on what Neighbors To Flag says: a late return is the event that makes the list change, so
 * the flagger reads the list then and issues Flag Neighbor for EVERY neighbor on it, naming the loans the list gives. Acting
 * on the whole list (not just the neighbor whose loan arrived) means a neighbor left unflagged earlier is picked up again.
 * A refusal is logged with the neighbor id and the flagger moves on; no person sees it. When the refusal is
 * `late-returns-changed` the list has moved on, so the flagger reads it again and acts on the updated entry. Acting twice on
 * the same neighbor is safe (INV-FLG-9).
 */
@Component
class LateReturnFlaggerHandler(
    private val readList: () -> NeighborsToFlag,
    private val send: (Any) -> ReactionOutcome,
    private val attempts: Int = LIST_CATCH_UP_ATTEMPTS,
    private val pause: () -> Unit = { Thread.sleep(LIST_CATCH_UP_PAUSE_MILLIS) },
    private val now: () -> Instant = Instant::now,
) {
    @Autowired
    constructor(queries: QueryGateway, reactions: ReactionDispatcher) : this(
        { queries.query(ListNeighborsToFlag(), NeighborsToFlag::class.java).join() },
        reactions::send,
    )

    /**
     * The list is built by its own processor, so it may not have applied a live late return yet. Read it until the loan just
     * returned shows on an entry, for a bounded time; a replayed (old) event is not waited for, as the list has had time to
     * apply it. Whatever the list holds when the wait ends is flagged, so a miss here is corrected by the next late return.
     */
    @EventHandler
    fun on(event: ToolReturned) {
        if (!event.late) return
        val waits = if (isLive(event)) attempts else 1
        var list = readList()
        var attempt = 1
        while (list.neighbors.none { event.loanId in it.loanIds } && attempt < waits) {
            pause()
            list = readList()
            attempt++
        }
        if (list.neighbors.none { event.loanId in it.loanIds }) {
            logger.info { "late-return-flagger loanId=${event.loanId.value} outcome=not-on-list" }
        }
        flag(list)
    }

    private fun isLive(event: ToolReturned) = Duration.between(event.returnedAt, now()) < LIVE_WINDOW

    /** Issues Flag Neighbor for every neighbor on [list], acting again on the updated entry after `late-returns-changed`. */
    fun flag(list: NeighborsToFlag) {
        list.neighbors.forEach { entry -> flagWithRetry(entry) }
    }

    private fun flagWithRetry(first: NeighborToFlag) {
        var entry = first
        repeat(REFLAG_ATTEMPTS) { attempt ->
            val outcome = send(FlagNeighbor(entry.neighborId, entry.loanIds))
            when (outcome) {
                is ReactionOutcome.Accepted -> {
                    logger.info { "late-return-flagger neighborId=${entry.neighborId.value} outcome=accepted" }
                    return
                }
                is ReactionOutcome.Refused -> {
                    logger.info {
                        "late-return-flagger neighborId=${entry.neighborId.value} outcome=refused reason=${outcome.reason}"
                    }
                    if (outcome.reason != LATE_RETURNS_CHANGED || attempt == REFLAG_ATTEMPTS - 1) return
                    val updated = readList().neighbors.firstOrNull { it.neighborId == entry.neighborId } ?: return
                    if (updated.loanIds.toSet() == entry.loanIds.toSet()) return
                    entry = updated
                }
            }
        }
    }
}

private const val LATE_RETURNS_CHANGED = "late-returns-changed"
private const val REFLAG_ATTEMPTS = 3
private const val LIST_CATCH_UP_ATTEMPTS = 20
private const val LIST_CATCH_UP_PAUSE_MILLIS = 100L
private val LIVE_WINDOW: Duration = Duration.ofMinutes(1)
