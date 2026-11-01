package com.toolshed.neighbors

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/**
 * List the neighbors: every account with all its fields, ordered by name ignoring case, then by when it signed up
 * (INV-NBRS-8). No paging.
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
                .sortedWith(
                    compareBy<StoredNeighbor> { it.name.lowercase() }
                        .thenBy { it.registeredAt }
                        .thenBy { it.sequence },
                ).map { it.toRow() },
        )

    private fun StoredNeighbor.toRow(): NeighborRow {
        val status = status()
        return NeighborRow(neighborId, name, email, registeredAt, status, canSuspend = status == "active")
    }
}
