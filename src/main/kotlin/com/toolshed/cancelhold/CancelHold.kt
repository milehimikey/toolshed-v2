package com.toolshed.cancelhold

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor
import org.axonframework.modelling.annotation.TargetEntityId

/** A holder giving up their hold. The hold's tool and holder are those on its Hold Placed. */
data class CancelHold(
    val neighborId: NeighborId,
    val holdId: HoldId,
) {
    @TargetEntityId
    private fun ledger(): HoldLedger = HoldLedger
}

/** The consistency boundary for cancellations: one id for the shed's holds. Not a hold. */
object HoldLedger

/** Identity checks that run before the command is sent (Amendment 6): signed in, and the holder named. */
fun Caller.requireCanceller(command: CancelHold) {
    requireNeighbor(
        neighborId = command.neighborId,
        notSignedIn = Refusal("INV-CNH-1", "Please sign in."),
        mismatch = Refusal("INV-CNH-1", "You can only cancel holds for yourself."),
    )
}
