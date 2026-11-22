package com.toolshed.toolmaintenance

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/** Answers the maintenance listing. No paging. */
@Component
class ToolMaintenanceQueryHandler(
    private val repository: ToolMaintenanceRepository,
) {
    @QueryHandler
    fun handle(query: ListToolsInMaintenance): ToolMaintenance = ToolMaintenance(repository.rows())
}
