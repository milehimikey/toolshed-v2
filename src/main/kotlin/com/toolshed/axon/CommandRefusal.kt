package com.toolshed.axon

/**
 * A command refusal carried as the command's result. Results are ordinary serialized payloads, so this
 * crosses Axon Server where an exception's type and details do not.
 */
data class CommandRefusal(
    val invariant: String,
    val reason: String,
    val sentence: String,
)
