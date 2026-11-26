package com.toolshed.toolinventory

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/** Answers the inventory listing. No paging. Overdue is worked out here, from today's date in the shed's zone (INV-TINV-9). */
@Component
class ToolInventoryQueryHandler(
    private val repository: ToolInventoryRepository,
    private val clock: Clock,
) {
    @QueryHandler
    fun handle(query: ListToolInventory): ToolInventory {
        val today = LocalDate.now(clock)
        return ToolInventory(repository.rows { dueDate -> today.isAfter(dueDate) })
    }
}
