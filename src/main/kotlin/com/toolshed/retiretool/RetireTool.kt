package com.toolshed.retiretool

import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import org.axonframework.modelling.annotation.TargetEntityId

/** The shed manager taking a tool out of the shed for good. */
data class RetireTool(
    val toolId: ToolId,
) {
    @TargetEntityId
    private fun ledger(): ToolLedger = ToolLedger
}

/** The consistency boundary for retirements: one id for the shed's tools and loans. Not a tool. */
object ToolLedger

/** Identity checks that run before the command is sent (Amendment 6): signed in, and the shed manager's role. */
fun Caller.requireRetirer() {
    requireShedManager(
        notSignedIn = Refusal("INV-RTR-1", "Please sign in."),
        notShedManager = Refusal("INV-RTR-1", "Only the shed manager can retire tools."),
    )
}
