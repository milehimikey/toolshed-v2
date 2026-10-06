package com.toolshed.flagneighbor

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import java.time.Instant

data class NeighborFlagged(
    val neighborId: NeighborId,
    val loanIds: List<LoanId>,
    val flaggedAt: Instant,
)
