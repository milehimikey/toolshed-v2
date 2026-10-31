package com.toolshed.holdstofulfill

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/** List tools to fulfill (INV-HTF-1, INV-HTF-2): every tool with no open loan, not retired, nothing set aside, and a hold waiting. */
@Component
class ListToolsToFulfillQueryHandler(
    private val repository: HoldsToFulfillRepository,
) {
    @QueryHandler
    fun handle(query: ListToolsToFulfill): List<HoldsToFulfillRow> = repository.toolsToFulfill()
}
