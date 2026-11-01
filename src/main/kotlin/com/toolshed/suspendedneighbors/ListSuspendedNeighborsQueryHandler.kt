package com.toolshed.suspendedneighbors

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/**
 * List suspended neighbors: every entry with all its fields, resolved now from current facts. Listed by name,
 * ignoring case, then by suspendedAt (INV-SPN-10). No paging.
 */
@Component
class ListSuspendedNeighborsQueryHandler(
    private val repository: SuspendedNeighborsRepository,
) {
    @QueryHandler
    fun handle(query: ListSuspendedNeighbors): SuspendedNeighbors =
        SuspendedNeighbors(
            repository
                .entries()
                .map { it to toRow(it) }
                .sortedWith(
                    compareBy<Pair<StoredSuspension, SuspendedNeighborRow>> { it.second.name.lowercase() }
                        .thenBy { it.first.suspendedAt }
                        .thenBy { it.first.sequence },
                ).map { it.second },
        )

    private fun toRow(entry: StoredSuspension) =
        SuspendedNeighborRow(
            neighborId = entry.neighborId,
            name = repository.neighborName(entry.neighborId).orEmpty(),
            email = repository.neighborEmail(entry.neighborId).orEmpty(),
            suspendedAt = entry.suspendedAt,
        )
}
