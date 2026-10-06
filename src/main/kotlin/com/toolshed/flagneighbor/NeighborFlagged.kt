package com.toolshed.flagneighbor

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant

data class NeighborFlagged(
    @field:EventTag(key = "neighborId")
    val neighborId: NeighborId,
    val loanIds: List<LoanId>,
    val flaggedAt: Instant,
)
