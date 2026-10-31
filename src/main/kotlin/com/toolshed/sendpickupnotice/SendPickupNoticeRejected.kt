package com.toolshed.sendpickupnotice

import com.toolshed.axon.CommandRejected

/** A refused pickup email. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class SendPickupNoticeRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)
