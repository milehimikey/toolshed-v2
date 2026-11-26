package com.toolshed.suspendedneighbors

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/**
 * List suspended neighbors: every entry with all its fields, resolved now from current facts. Listed by name,
 * ignoring case, then by suspendedAt, then by when the entry was created (INV-SPN-10). No paging.
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
                .sortedWith(
                    compareBy<StoredSuspension> { repository.neighborName(it.neighborId).orEmpty().lowercase() }
                        .thenBy { it.suspendedAt }
                        .thenBy { it.sequence },
                ).map {
                    SuspendedNeighborRow(
                        neighborId = it.neighborId,
                        name = repository.neighborName(it.neighborId).orEmpty(),
                        email = repository.neighborEmail(it.neighborId).orEmpty(),
                        suspendedAt = it.suspendedAt,
                    )
                },
        )
}
