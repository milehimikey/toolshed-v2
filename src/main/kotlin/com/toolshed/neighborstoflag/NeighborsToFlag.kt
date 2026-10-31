package com.toolshed.neighborstoflag

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId

/** One neighbor the Late Return Flagger should flag, with every late-returned loan that counts toward the flag. */
data class NeighborToFlag(
    val neighborId: NeighborId,
    val loanIds: List<LoanId>,
)

/** The query's answer: each neighbor with at least 3 late returns in the window (INV-NTF-2). No paging; order is unspecified. */
data class NeighborsToFlag(
    val neighbors: List<NeighborToFlag>,
)

/** List neighbors to flag. Takes nothing; read only by the Late Return Flagger. */
class ListNeighborsToFlag
