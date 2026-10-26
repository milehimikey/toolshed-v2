package com.toolshed.toolcatalog

import com.toolshed.addtool.ToolAdded
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.extendloan.LoanExtended
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

/**
 * Applies exactly the eleven events the Tool Catalog view reads (INV-TCAT-9). Overdue notices,
 * pickup emails and email changes are not in the view, so they have no handler here.
 */
@Component
class ToolCatalogProjection(
    private val repository: ToolCatalogRepository,
) {
    @EventHandler
    fun on(event: ToolAdded) {
        repository.addTool(event.toolId, event.name, event.description, event.defaultCheckoutPeriodDays, event.addedAt)
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
    fun on(event: HoldPlaced) {
        repository.placeHold(event.holdId, event.toolId, event.neighborId)
    }

    @EventHandler
    fun on(event: HoldCancelled) {
        repository.cancelHold(event.holdId)
    }

    @EventHandler
    fun on(event: HoldFulfilled) {
        repository.fulfillHold(event.holdId, event.toolId)
    }

    @EventHandler
    fun on(event: HoldExpired) {
        repository.expireHold(event.holdId)
    }

    @EventHandler
    fun on(event: ToolRetired) {
        repository.retire(event.toolId)
    }

    @EventHandler
    fun on(event: ToolLost) {
        repository.recordLost(event.toolId)
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.closeAccount(event.neighborId)
    }
}
