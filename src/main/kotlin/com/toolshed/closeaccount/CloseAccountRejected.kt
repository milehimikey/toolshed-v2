package com.toolshed.closeaccount

import com.toolshed.axon.CommandRejected

class CloseAccountRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)
