package com.toolshed.changeemail

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor
import org.axonframework.modelling.annotation.TargetEntityId

/**
 * A neighbor changing the email the shed uses for their own account. [email] is nullable so that a
 * missing or blank value reaches the decider and is refused as `email-missing`.
 */
data class ChangeEmail(
    val neighborId: NeighborId,
    val email: String?,
) {
    @TargetEntityId
    private fun ledger(): EmailLedger = EmailLedger
}

/** The consistency boundary for email changes: one id for the shed's accounts and emails. Not an account. */
object EmailLedger

/** Identity checks that run before the command is sent (Amendment 6): signed in, and the account is theirs. */
fun Caller.requireAccountHolder(command: ChangeEmail) {
    requireNeighbor(
        neighborId = command.neighborId,
        notSignedIn = Refusal("INV-CEM-13", "Please sign in."),
        mismatch = Refusal("INV-CEM-1", "You can only change your own email."),
    )
}
