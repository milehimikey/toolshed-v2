package com.toolshed.myaccount

import com.toolshed.axon.NeighborId

/** One account on its owner's My Account screen. */
data class MyAccountRow(
    val neighborId: NeighborId,
    val name: String,
    val email: String,
    val status: String,
    val canChangeEmail: Boolean,
    val canCloseAccount: Boolean,
)

/** The query's answer: the one account asked for, or null when no account has that neighbor ID. */
data class MyAccount(
    val account: MyAccountRow?,
)

/** My Account for one neighbor, asked by that neighbor's id. */
data class GetMyAccount(
    val neighborId: NeighborId,
)
