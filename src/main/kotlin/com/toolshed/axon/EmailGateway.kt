package com.toolshed.axon

/** Hand-off key for an outgoing notice: the loan or hold it concerns. */
sealed interface EmailKey {
    data class ForLoan(
        val loanId: LoanId,
    ) : EmailKey

    data class ForHold(
        val holdId: HoldId,
    ) : EmailKey
}

enum class HandOffResult { ACCEPTED, REFUSED }

/**
 * Hands an outgoing email to the provider. The provider sends at most one email per key.
 * Payload fields are provisional; the notice slices own what they carry.
 */
interface EmailGateway {
    fun handOff(
        key: EmailKey,
        to: String,
        subject: String,
        body: String,
    ): HandOffResult
}
