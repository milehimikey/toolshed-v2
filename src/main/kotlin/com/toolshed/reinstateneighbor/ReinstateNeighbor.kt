package com.toolshed.reinstateneighbor

import com.toolshed.axon.NeighborId
import org.axonframework.modelling.annotation.TargetEntityId

/** No reason or note field, and no "from" status: a late copy of an older request counts as a new request. */
data class ReinstateNeighbor(
    @TargetEntityId val neighborId: NeighborId,
)
