package com.toolshed.placehold

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor
import org.axonframework.modelling.annotation.TargetEntityId

/**
 * A neighbor getting in line for a tool. [holdId] is chosen by the caller, so a retried request is
 * recognised as the same hold.
 */
data class PlaceHold(
    val holdId: HoldId,
    val toolId: ToolId,
    val neighborId: NeighborId,
) {
    @TargetEntityId
    private fun ledger(): HoldLedger = HoldLedger
}

/** The consistency boundary for holds: one id for the shed's tools, loans, holds and accounts. Not a hold. */
object HoldLedger

/** Identity checks that run before the command is sent (Amendment 6): signed in, and the holder named. */
fun Caller.requireHolder(command: PlaceHold) {
    requireNeighbor(
        neighborId = command.neighborId,
        notSignedIn = Refusal("INV-HOLD-15", "Please sign in."),
        mismatch = Refusal("INV-HOLD-1", "You can only place holds for yourself."),
    )
}
