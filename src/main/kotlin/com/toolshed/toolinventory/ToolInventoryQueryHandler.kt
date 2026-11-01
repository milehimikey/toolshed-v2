package com.toolshed.toolinventory

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component
import java.time.Clock

/** Answers the inventory listing. No paging. Overdue is worked out here, against today in the shed's zone (INV-TINV-9). */
@Component
class ToolInventoryQueryHandler(
    private val repository: ToolInventoryRepository,
    private val clock: Clock,
) {
    @QueryHandler
    fun handle(query: ListToolInventory): ToolInventory = ToolInventory(repository.rows(clock.instant().atZone(clock.zone).toLocalDate()))
}
