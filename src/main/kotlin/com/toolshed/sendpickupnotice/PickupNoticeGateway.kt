package com.toolshed.sendpickupnotice

import com.toolshed.axon.ReactionOutcome
import com.toolshed.pickupnoticestosend.PickupNoticesToSendReaction
import com.toolshed.pickupnoticestosend.PickupNoticesToSendRow
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * The Pickup Notice Gateway's reading half: read Pickup Notices To Send once and issue Send Pickup Notice for each item, with
 * the item's content. A refusal is logged with the hold id and nothing more is done; a repeat records nothing. It runs on the
 * list's own processor, after the projection (Amendment 11), so the list has applied the event that woke it. No waiting, no re-read.
 */
class PickupNoticeGateway(
    private val list: () -> List<PickupNoticesToSendRow>,
    private val send: (SendPickupNotice) -> ReactionOutcome,
) {
    fun run() = list().forEach { handle(it) }

    private fun handle(item: PickupNoticesToSendRow) {
        val command = SendPickupNotice(item.holdId, item.holderEmail, item.holderName, item.toolName, item.collectByDate)
        when (val outcome = send(command)) {
            is ReactionOutcome.Accepted -> logger.debug { "pickup-notice-gateway holdId=${item.holdId.value} outcome=accepted" }
            is ReactionOutcome.Refused ->
                logger.info { "pickup-notice-gateway holdId=${item.holdId.value} outcome=refused reason=${outcome.reason}" }
        }
    }
}

/** The reaction the Pickup Notices To Send projection invokes after each list-changing event's repository write. */
class PickupNoticeGatewayHandler(
    private val gateway: PickupNoticeGateway,
) : PickupNoticesToSendReaction {
    override fun listChanged(event: Any) = gateway.run()
}
