package com.toolshed.web

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.NeighborId

const val NOT_SIGNED_IN = "not-signed-in"
const val REQUESTER_MISMATCH = "requester-mismatch"
const val NOT_SHED_MANAGER = "not-shed-manager"

/*
 * The identity checks every slice runs first (Amendment 6). Each takes the slice's own invariant id
 * and the slice doc's sentence for each refusal it can make.
 */

fun Caller.requireSignedIn(
    invariant: String,
    sentence: String,
) {
    if (!isSignedIn) throw CommandRejected(invariant, NOT_SIGNED_IN, sentence)
}

fun Caller.requireNeighbor(
    neighborId: NeighborId,
    invariant: String,
    notSignedInSentence: String,
    mismatchSentence: String,
) {
    requireSignedIn(invariant, notSignedInSentence)
    if (this.neighborId != neighborId) throw CommandRejected(invariant, REQUESTER_MISMATCH, mismatchSentence)
}

fun Caller.requireShedManager(
    invariant: String,
    notSignedInSentence: String,
    notShedManagerSentence: String,
) {
    requireSignedIn(invariant, notSignedInSentence)
    if (!isShedManager) throw CommandRejected(invariant, NOT_SHED_MANAGER, notShedManagerSentence)
}
