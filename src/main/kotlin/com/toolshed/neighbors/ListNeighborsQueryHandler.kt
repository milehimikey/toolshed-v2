package com.toolshed.neighbors

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/**
 * List the neighbors: every account with all its fields. Listed by name, ignoring case, then by when the
 * account signed up (INV-NBRS-8). No paging.
 */
@Component
class ListNeighborsQueryHandler(
    private val repository: NeighborsRepository,
) {
    @QueryHandler
    fun handle(query: ListNeighbors): Neighbors =
        Neighbors(
            repository
                .all()
                .sortedWith(compareBy<StoredNeighbor> { it.name.lowercase() }.thenBy { it.registeredAt }.thenBy { it.sequence })
                .map { NeighborRow(it.neighborId, it.name, it.email, it.registeredAt, it.status) },
        )
}
