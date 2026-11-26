package com.toolshed.toolcatalog

import com.toolshed.addtool.ToolAdded
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.completetoolmaintenance.ToolMaintenanceCompleted
import com.toolshed.expirehold.HoldExpired
import com.toolshed.extendloan.LoanExtended
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceScheduled
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Applies exactly the thirteen events the Tool Catalog view reads (INV-TCAT-9). Overdue notices,
 * pickup emails and email changes are not in the view, so they have no handler here.
 */
@Component
class ToolCatalogProjection(
    private val repository: ToolCatalogRepository,
) {
    @EventHandler
    fun on(event: ToolAdded) {
        repository.addTool(event.toolId, event.name, event.description, event.defaultCheckoutPeriodDays, event.addedAt)
        logger.debug { "tool-catalog toolId=${event.toolId.value} outcome=added" }
    }

    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.checkOut(event.loanId, event.toolId, event.neighborId, event.dueDate)
        logger.debug { "tool-catalog loanId=${event.loanId.value} outcome=checked-out" }
    }

    @EventHandler
    fun on(event: LoanExtended) {
        repository.extend(event.loanId, event.dueDate)
        logger.debug { "tool-catalog loanId=${event.loanId.value} outcome=extended" }
    }

    @EventHandler
    fun on(event: ToolReturned) {
        repository.endLoan(event.loanId)
        logger.debug { "tool-catalog loanId=${event.loanId.value} outcome=returned" }
    }

    @EventHandler
    fun on(event: HoldPlaced) {
        repository.placeHold(event.holdId, event.toolId, event.neighborId)
        logger.debug { "tool-catalog holdId=${event.holdId.value} outcome=hold-placed" }
    }

    @EventHandler
    fun on(event: HoldCancelled) {
        repository.cancelHold(event.holdId)
        logger.debug { "tool-catalog holdId=${event.holdId.value} outcome=hold-cancelled" }
    }

    @EventHandler
    fun on(event: HoldFulfilled) {
        repository.fulfillHold(event.holdId, event.toolId)
        logger.debug { "tool-catalog holdId=${event.holdId.value} outcome=hold-fulfilled" }
    }

    @EventHandler
    fun on(event: HoldExpired) {
        repository.expireHold(event.holdId)
        logger.debug { "tool-catalog holdId=${event.holdId.value} outcome=hold-expired" }
    }

    @EventHandler
    fun on(event: ToolRetired) {
        repository.retire(event.toolId)
        logger.debug { "tool-catalog toolId=${event.toolId.value} outcome=retired" }
    }

    @EventHandler
    fun on(event: ToolLost) {
        repository.recordLost(event.toolId)
        logger.debug { "tool-catalog toolId=${event.toolId.value} outcome=lost" }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.closeAccount(event.neighborId)
        logger.debug { "tool-catalog neighborId=${event.neighborId.value} outcome=account-closed" }
    }

    @EventHandler
    fun on(event: ToolMaintenanceScheduled) {
        repository.scheduleMaintenance(event.toolId, event.scheduledAt)
        logger.debug { "tool-catalog toolId=${event.toolId.value} outcome=maintenance-scheduled" }
    }

    @EventHandler
    fun on(event: ToolMaintenanceCompleted) {
        repository.completeMaintenance(event.toolId, event.completedAt)
        logger.debug { "tool-catalog toolId=${event.toolId.value} outcome=maintenance-completed" }
    }
}
