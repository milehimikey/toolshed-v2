package com.toolshed.flagneighbor

import com.toolshed.axon.CommandRejected

/** A refused Flag Neighbor. The name is the contract; the sentence is the log text. */
class FlagNeighborRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)
