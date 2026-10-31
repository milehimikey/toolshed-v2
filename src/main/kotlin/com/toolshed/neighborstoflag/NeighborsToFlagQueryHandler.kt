package com.toolshed.neighborstoflag

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component
import java.time.Clock

/** Answers the list for the Late Return Flagger. The window is worked out here against today in the shed's zone (INV-NTF-2). */
@Component
class NeighborsToFlagQueryHandler(
    private val repository: NeighborsToFlagRepository,
    private val clock: Clock,
) {
    @QueryHandler
    fun handle(query: ListNeighborsToFlag): NeighborsToFlag =
        NeighborsToFlag(repository.neighborsToFlag(clock.instant().atZone(clock.zone).toLocalDate()))
}
