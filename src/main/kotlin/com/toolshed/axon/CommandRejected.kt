package com.toolshed.axon

/**
 * A refused command. [invariant] is the slice doc's INV id, [reason] the stable kebab-case
 * name, and the message the doc sentence.
 */
open class CommandRejected(
    val invariant: String,
    val reason: String,
    sentence: String,
) : RuntimeException(sentence)
