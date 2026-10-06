package com.toolshed.addtool

import com.toolshed.axon.CommandRejected

class AddToolRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)
