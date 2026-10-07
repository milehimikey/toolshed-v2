package com.toolshed.neighbors

import org.axonframework.messaging.queryhandling.annotation.QueryHandler

/** Answers the list query from the view (no paging: every account, in the view's order). */
class NeighborsQueryHandler(
    private val view: NeighborsView,
) {
    @QueryHandler
    fun list(query: ListNeighbors): NeighborList = NeighborList(view.list())
}
