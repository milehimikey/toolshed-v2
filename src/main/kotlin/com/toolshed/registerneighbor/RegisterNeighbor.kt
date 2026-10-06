package com.toolshed.registerneighbor

import com.toolshed.axon.NeighborId
import org.axonframework.modelling.annotation.TargetEntityId

/**
 * A neighbor signing themselves up. [name] and [email] are nullable so that a missing field
 * reaches the decider and is refused as `name-missing` / `email-missing`, not as a malformed body.
 */
data class RegisterNeighbor(
    val neighborId: NeighborId,
    val name: String?,
    val email: String?,
) {
    @TargetEntityId
    private fun registry(): NeighborRegistry = NeighborRegistry
}
