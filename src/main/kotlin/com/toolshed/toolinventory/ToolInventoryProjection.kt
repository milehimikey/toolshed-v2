package com.toolshed.toolinventory

import com.toolshed.addtool.ToolAdded
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

/**
 * Applies the events the Tool Inventory view reads (its alsoReads). Holds, notices and emails are not in the view,
 * so they have no handler here (INV-TINV-3).
 */
@Component
class ToolInventoryProjection(
    private val repository: ToolInventoryRepository,
) {
    @EventHandler
    fun on(event: ToolAdded) {
        repository.addTool(event.toolId, event.name, event.description, event.defaultCheckoutPeriodDays, event.addedAt)
    }

    @EventHandler
    fun on(event: NeighborRegistered) {
        repository.registerNeighbor(event.neighborId, event.name)
    }

    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.checkOut(event.loanId, event.toolId, event.neighborId, event.dueDate)
    }

    @EventHandler
    fun on(event: LoanExtended) {
        repository.extend(event.loanId, event.dueDate)
    }

    @EventHandler
    fun on(event: ToolReturned) {
        repository.endLoan(event.loanId)
    }

    @EventHandler
    fun on(event: ToolRetired) {
        repository.retire(event.toolId)
    }

    @EventHandler
    fun on(event: ToolLost) {
        repository.recordLost(event.loanId, event.toolId)
    }
}
