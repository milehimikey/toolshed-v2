package com.toolshed.myaccount

import com.toolshed.axon.NeighborId

/** My Account for one neighbor, asked by that neighbor's id. */
data class GetMyAccount(
    val neighborId: NeighborId,
)

/** The query's single answer. The account is null when no account has that neighbor id (not found). */
data class MyAccountLookup(
    val account: MyAccountRow?,
)
