package com.toolshed.expirehold

import com.toolshed.axon.HoldId
import org.axonframework.modelling.annotation.TargetEntityId

/** End a set-aside hold whose collect-by date has passed. Internal: only the Pickup Expirer issues it. */
data class ExpireHold(
    val holdId: HoldId,
) {
    @TargetEntityId
    private fun ledger(): ExpireLedger = ExpireLedger
}

/** The consistency boundary for expiries: one id for the shed's holds and loans, so one tool is decided at a time. */
object ExpireLedger
