package com.toolshed.expirehold

import com.toolshed.axon.CommandRejected

/** A refused expiry. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class ExpireHoldRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)
