package com.toolshed.fulfillhold

import com.toolshed.axon.CommandRejected

/** A refused fulfilment. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class FulfillHoldRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)
