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
 * An empty read is therefore re-read after each of [catchUpDelaysMillis] (a bounded wait for the list to catch up)
 * before the fulfiller concludes there is nothing to do.
 */
class HoldFulfiller(
    private val list: () -> List<HoldsToFulfillRow>,
    private val send: (FulfillHold) -> ReactionOutcome,
    private val catchUpDelaysMillis: List<Long> = emptyList(),
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) {
    private fun readList(): List<HoldsToFulfillRow> {
        var items = list()
        for (delay in catchUpDelaysMillis) {
            if (items.isNotEmpty()) break
            pause(delay)
            items = list()
        }
        return items
    }

    fun run() {
        readList().forEach { item ->
            when (val outcome = send(FulfillHold(item.holdId))) {
                is ReactionOutcome.Accepted -> logger.debug { "hold-fulfiller holdId=${item.holdId.value} outcome=accepted" }
                is ReactionOutcome.Refused ->
                    logger.info { "hold-fulfiller holdId=${item.holdId.value} outcome=refused reason=${outcome.reason}" }
            }
        }
    }
}
