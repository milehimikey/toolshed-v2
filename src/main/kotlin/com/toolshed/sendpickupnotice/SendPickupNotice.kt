package com.toolshed.sendpickupnotice

import com.toolshed.axon.HoldId
import org.axonframework.modelling.annotation.TargetEntityId
import java.time.LocalDate

/**
 * Record that the pickup email for a set-aside hold is being sent. Internal: only the Pickup Notice Gateway issues it,
 * with the content from the Pickup Notices To Send item. The content is compared and recorded after trimming (Amendment 7).
 */
data class SendPickupNotice(
    val holdId: HoldId,
    val sentTo: String,
    val holderName: String,
    val toolName: String,
    val collectByDate: LocalDate,
) {
    init {
        require(sentTo.isNotBlank()) { "sentTo is required" }
        require(holderName.isNotBlank()) { "holderName is required" }
        require(toolName.isNotBlank()) { "toolName is required" }
    }

    @TargetEntityId
    private fun key() = PickupNoticeKey(holdId)
}

/** The entity id for one hold's pickup email. A data class, because a value-class id is stored as a bare UUID. */
data class PickupNoticeKey(
    val holdId: HoldId,
)
