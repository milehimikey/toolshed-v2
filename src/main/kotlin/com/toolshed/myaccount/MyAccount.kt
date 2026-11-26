package com.toolshed.myaccount

import com.toolshed.axon.NeighborId

/** One account on its owner's My Account screen. [status] is `active`, `suspended` or `closed`. */
data class MyAccount(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val status: String,
    val canChangeEmail: Boolean,
    val canCloseAccount: Boolean,
)

/** My Account for one neighbor, asked by that neighbor's id. */
data class GetMyAccount(
    val neighborId: NeighborId,
)

/** The query's answer: the one account asked for, or null when no account has that neighbor id (not found). */
data class MyAccountAnswer(
    val account: MyAccount?,
)
