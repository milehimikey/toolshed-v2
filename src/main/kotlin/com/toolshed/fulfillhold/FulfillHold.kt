package com.toolshed.fulfillhold

import com.toolshed.axon.HoldId
import org.axonframework.modelling.annotation.TargetEntityId

/** Set a tool aside for the hold at the front of its queue. Internal: only the Hold Fulfiller issues it. */
data class FulfillHold(
    val holdId: HoldId,
) {
    @TargetEntityId
    private fun ledger(): FulfillLedger = FulfillLedger
}

/** The consistency boundary for fulfilments: one id for the shed's holds and loans, so one tool is decided at a time. */
object FulfillLedger
