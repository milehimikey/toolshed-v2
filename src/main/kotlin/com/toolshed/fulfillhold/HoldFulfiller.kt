package com.toolshed.fulfillhold

import com.toolshed.axon.ReactionOutcome
import com.toolshed.holdstofulfill.HoldsToFulfillRow
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * The Hold Fulfiller's work: read Holds To Fulfill and issue Fulfill Hold for each item. A refusal is logged with the
 * hold id and the fulfiller moves on; nothing else is done about it, because the list will show the tool again with
 * its new front hold if there is one.
 *
 * The list is fed by its own processor, so a wake-up can arrive before the list has applied the event that caused it.
 * The list may be empty, or may still show only tools this run has already fulfilled (it has not yet applied their
 * Hold Fulfilled, nor shown the tool that woke us). So after acting on the first read, the list is re-read after
 * each of [catchUpDelaysMillis] and any item not yet handled in this run is acted on.
 */
class HoldFulfiller(
    private val list: () -> List<HoldsToFulfillRow>,
    private val send: (FulfillHold) -> ReactionOutcome,
    private val catchUpDelaysMillis: List<Long> = emptyList(),
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun run() {
        val handled = mutableSetOf<HoldsToFulfillRow>()
        val readsAfterWaiting = catchUpDelaysMillis.map { { pause(it) } }
        list().filter { handled.add(it) }.forEach { handle(it) }
        // The list may not yet show the tool that woke us (or may still show tools already fulfilled), so keep
        // re-reading for the bounded wait and act on whatever new item appears.
        readsAfterWaiting.forEach { wait ->
            wait()
            list().filter { handled.add(it) }.forEach { handle(it) }
        }
    }

    private fun handle(item: HoldsToFulfillRow) {
        when (val outcome = send(FulfillHold(item.holdId))) {
            is ReactionOutcome.Accepted -> logger.debug { "hold-fulfiller holdId=${item.holdId.value} outcome=accepted" }
            is ReactionOutcome.Refused ->
                logger.info { "hold-fulfiller holdId=${item.holdId.value} outcome=refused reason=${outcome.reason}" }
        }
    }
}
