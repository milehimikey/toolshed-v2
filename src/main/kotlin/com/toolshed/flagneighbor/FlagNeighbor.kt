package com.toolshed.flagneighbor

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import org.axonframework.modelling.annotation.TargetEntityId

/**
 * Flags a neighbor for the late returns behind the flag, by loan, as Neighbors To Flag gave them. Issued only by the
 * Late Return Flagger. An empty loan list is bad input, rejected before any rule is checked.
 */
data class FlagNeighbor(
    @TargetEntityId val neighborId: NeighborId,
    val loanIds: List<LoanId>,
) {
    init {
        require(loanIds.isNotEmpty()) { "loanIds must not be empty" }
    }
}
