package com.toolshed.web

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.NeighborId

const val NOT_SIGNED_IN = "not-signed-in"
const val REQUESTER_MISMATCH = "requester-mismatch"
const val NOT_SHED_MANAGER = "not-shed-manager"

/**
 * One refusal a slice can make: the invariant its doc cites for it, and the doc's sentence.
 * Each refusal carries its own invariant, because a slice's docs often cite different INV ids
 * for not-signed-in and for mismatch or role.
 */
data class Refusal(
    val invariant: String,
    val sentence: String,
)

/*
 * The identity checks every slice runs first (Amendment 6), each with the slice's own refusals.
 */

fun Caller.requireSignedIn(notSignedIn: Refusal) {
    if (!isSignedIn) throw CommandRejected(notSignedIn.invariant, NOT_SIGNED_IN, notSignedIn.sentence)
}

fun Caller.requireNeighbor(
    neighborId: NeighborId,
    notSignedIn: Refusal,
    mismatch: Refusal,
) {
    requireSignedIn(notSignedIn)
    if (this.neighborId != neighborId) throw CommandRejected(mismatch.invariant, REQUESTER_MISMATCH, mismatch.sentence)
}

fun Caller.requireShedManager(
    notSignedIn: Refusal,
    notShedManager: Refusal,
) {
    requireSignedIn(notSignedIn)
    if (!isShedManager) throw CommandRejected(notShedManager.invariant, NOT_SHED_MANAGER, notShedManager.sentence)
}
