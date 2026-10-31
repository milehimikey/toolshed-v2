package com.toolshed.expirehold

import com.toolshed.axon.ReactionOutcome
import com.toolshed.pickupstoexpire.PickupToExpireRow
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * The Pickup Expirer's work: read Pickups To Expire once and issue Expire Hold for each lapsed set-aside. A refusal is
 * logged with the hold id and the expirer moves on. Expire Hold is safe to repeat, so reading the same row twice is harmless.
 */
class PickupExpirer(
    private val list: () -> List<PickupToExpireRow>,
    private val send: (ExpireHold) -> ReactionOutcome,
) {
    fun run() = list().forEach { handle(it) }

    private fun handle(item: PickupToExpireRow) {
        when (val outcome = send(ExpireHold(item.holdId))) {
            is ReactionOutcome.Accepted -> logger.debug { "pickup-expirer holdId=${item.holdId.value} outcome=accepted" }
            is ReactionOutcome.Refused ->
                logger.info { "pickup-expirer holdId=${item.holdId.value} outcome=refused reason=${outcome.reason}" }
        }
    }
}
