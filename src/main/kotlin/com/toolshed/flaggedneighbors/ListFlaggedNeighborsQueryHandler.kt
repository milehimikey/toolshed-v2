package com.toolshed.flaggedneighbors

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/**
 * List flagged neighbors: every entry with all its fields and late returns, resolved now from current facts.
 * Listed by name, ignoring case, then by when the entry was created: the flaggedAt of its first flag (INV-FLN-10).
 * No paging.
 */
@Component
class ListFlaggedNeighborsQueryHandler(
    private val repository: FlaggedNeighborsRepository,
) {
    @QueryHandler
    fun handle(query: ListFlaggedNeighbors): FlaggedNeighbors =
        FlaggedNeighbors(
            repository
                .entries()
                .filter { it.flags.isNotEmpty() }
                .map { it to toRow(it) }
                .sortedWith(
                    compareBy<Pair<StoredEntry, FlaggedNeighborRow>> { it.second.name.lowercase() }
                        .thenBy {
                            it.first.flags
                                .first()
                                .flaggedAt
                        }.thenBy { it.first.sequence },
                ).map { it.second },
        )

    private fun toRow(entry: StoredEntry): FlaggedNeighborRow =
        FlaggedNeighborRow(
            neighborId = entry.neighborId,
            name = repository.neighborName(entry.neighborId).orEmpty(),
            email = repository.neighborEmail(entry.neighborId).orEmpty(),
            flagCount = entry.flags.size,
            latestFlaggedAt = entry.flags.maxOf { it.flaggedAt },
            lateReturns =
                entry.flags.flatMap { flag ->
                    flag.loanIds.mapNotNull { loanId ->
                        val loan = repository.loanOf(loanId)
                        val returnedOn = loan?.returnedOn
                        if (loan == null || returnedOn == null) {
                            null
                        } else {
                            LateReturnRow(
                                loanId = loanId,
                                toolName = repository.toolName(loan.toolId).orEmpty(),
                                dueDate = loan.dueDate,
                                returnedOn = returnedOn,
                                flaggedAt = flag.flaggedAt,
                            )
                        }
                    }
                },
        )
}
