package com.toolshed.reinstateneighbor

import com.toolshed.axon.CommandRejected

class ReinstateNeighborRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)
