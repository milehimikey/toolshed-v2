package com.toolshed.toolinventory

import com.toolshed.addtool.ToolAdded
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Applies exactly the seven events the Tool Inventory view reads (INV-TINV-3). Holds and overdue notices
 * have no handler here, so they cannot change an entry.
 */
@Component
class ToolInventoryProjection(
    private val repository: ToolInventoryRepository,
) {
    @EventHandler
    fun on(event: ToolAdded) {
        repository.addTool(event.toolId, event.name, event.description, event.defaultCheckoutPeriodDays, event.addedAt)
        logger.debug { "tool-inventory toolId=${event.toolId.value} outcome=tool-added" }
    }

    @EventHandler
    fun on(event: NeighborRegistered) {
        repository.registerNeighbor(event.neighborId, event.name)
        logger.debug { "tool-inventory neighborId=${event.neighborId.value} outcome=neighbor-registered" }
    }

    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.checkOut(event.loanId, event.toolId, event.neighborId, event.dueDate)
        logger.debug { "tool-inventory loanId=${event.loanId.value} toolId=${event.toolId.value} outcome=checked-out" }
    }

    @EventHandler
    fun on(event: LoanExtended) {
        repository.extend(event.loanId, event.dueDate)
        logger.debug { "tool-inventory loanId=${event.loanId.value} outcome=extended" }
    }

    @EventHandler
    fun on(event: ToolReturned) {
        repository.endLoan(event.loanId)
        logger.debug { "tool-inventory loanId=${event.loanId.value} outcome=returned" }
    }

    @EventHandler
    fun on(event: ToolRetired) {
        repository.retire(event.toolId)
        logger.debug { "tool-inventory toolId=${event.toolId.value} outcome=retired" }
    }

    @EventHandler
    fun on(event: ToolLost) {
        repository.recordLost(event.toolId)
        logger.debug { "tool-inventory toolId=${event.toolId.value} outcome=lost" }
    }
}
