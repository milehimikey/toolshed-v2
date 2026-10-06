package com.toolshed.suspendneighbor

import com.toolshed.axon.NeighborId
import org.axonframework.modelling.annotation.TargetEntityId

/** Suspend Neighbor: the shed manager suspends one neighbor. The command carries no reason and no "from" status. */
data class SuspendNeighbor(
    val neighborId: NeighborId,
) {
    @TargetEntityId
    private fun target() = NeighborStatusId(neighborId)
}

/** The entity id for one neighbor's status. A data class, because a value-class id is stored as a bare UUID. */
data class NeighborStatusId(
    val neighborId: NeighborId,
)
