package com.toolshed.closeaccount

import com.toolshed.axon.NeighborId
import org.axonframework.modelling.annotation.TargetEntityId

/** The account to close. The request must come from that neighbor (checked at the edge, before this runs). */
data class CloseAccount(
    @TargetEntityId val neighborId: NeighborId,
)
