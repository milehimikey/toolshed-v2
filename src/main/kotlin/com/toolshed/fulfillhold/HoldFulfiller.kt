package com.toolshed.fulfillhold

import com.toolshed.axon.ReactionOutcome
import com.toolshed.holdstofulfill.HoldsToFulfillRow
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * The Hold Fulfiller's work: read Holds To Fulfill and issue Fulfill Hold for each item. A refusal is logged with the
 * hold id and the fulfiller moves on; nothing else is done about it, because the list will show the tool again with
 * its new front hold if there is one.
 */
class HoldFulfiller(
    private val list: () -> List<HoldsToFulfillRow>,
    private val send: (FulfillHold) -> ReactionOutcome,
) {
    fun run() {
        list().forEach { item ->
            when (val outcome = send(FulfillHold(item.holdId))) {
                is ReactionOutcome.Accepted -> logger.debug { "hold-fulfiller holdId=${item.holdId.value} outcome=accepted" }
                is ReactionOutcome.Refused ->
                    logger.info { "hold-fulfiller holdId=${item.holdId.value} outcome=refused reason=${outcome.reason}" }
            }
        }
    }
}
