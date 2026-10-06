package com.toolshed.recordpickupnoticefailure

import com.toolshed.axon.HoldId
import org.axonframework.modelling.annotation.TargetEntityId

/**
 * Records that the provider's final report said the pickup email for [holdId] failed. [reason] is `refused` or
 * `bounced`. The translation rejects any other value before a command is built.
 */
data class RecordPickupNoticeFailure(
    val holdId: HoldId,
    val reason: String,
) {
    init {
        require(reason in FAILURE_REASONS) { "reason must be one of $FAILURE_REASONS" }
    }

    @TargetEntityId
    private fun key() = PickupNoticeKey(holdId)

    companion object {
        val FAILURE_REASONS = setOf("refused", "bounced")
    }
}

/** The entity id for one pickup email's failure. A data class, because a value-class id is stored as a bare UUID. */
data class PickupNoticeKey(
    val holdId: HoldId,
)
