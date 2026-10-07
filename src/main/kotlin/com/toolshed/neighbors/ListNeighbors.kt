package com.toolshed.neighbors

/** The Neighbors list query. It takes nothing: it returns every account. */
data object ListNeighbors

/** The list query's single answer: every row, in the view's order. One response, so the query is one result. */
data class NeighborList(
    val rows: List<NeighborRow>,
)
