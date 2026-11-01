package com.toolshed.sendoverduenotice

import com.toolshed.axon.CommandRejected

/** A refused overdue email. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class SendOverdueNoticeRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)
