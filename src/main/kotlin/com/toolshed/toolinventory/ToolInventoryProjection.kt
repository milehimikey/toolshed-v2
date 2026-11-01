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
 * Applies exactly the seven events the Tool Inventory view reads (INV-TINV-3). Holds and overdue notices are not
 * in the view, so they have no handler here.
 */
@Component
class ToolInventoryProjection(
    private val repository: ToolInventoryRepository,
) {
    @EventHandler
    fun on(event: ToolAdded) {
        logger.debug { "tool-inventory tool-added toolId=${event.toolId.value}" }
        repository.addTool(event.toolId, event.name, event.description, event.defaultCheckoutPeriodDays, event.addedAt)
    }

    @EventHandler
    fun on(event: NeighborRegistered) {
        logger.debug { "tool-inventory neighbor-registered neighborId=${event.neighborId.value}" }
        repository.registerNeighbor(event.neighborId, event.name)
    }

    @EventHandler
    fun on(event: ToolCheckedOut) {
        logger.debug {
            "tool-inventory tool-checked-out loanId=${event.loanId.value} " +
                "toolId=${event.toolId.value} neighborId=${event.neighborId.value}"
        }
        repository.checkOut(event.loanId, event.toolId, event.neighborId, event.dueDate)
    }

    @EventHandler
    fun on(event: LoanExtended) {
        logger.debug { "tool-inventory loan-extended loanId=${event.loanId.value}" }
        repository.extend(event.loanId, event.dueDate)
    }

    @EventHandler
    fun on(event: ToolReturned) {
        logger.debug { "tool-inventory tool-returned loanId=${event.loanId.value}" }
        repository.endLoan(event.loanId)
    }

    @EventHandler
    fun on(event: ToolRetired) {
        logger.debug { "tool-inventory tool-retired toolId=${event.toolId.value}" }
        repository.retire(event.toolId)
    }

    @EventHandler
    fun on(event: ToolLost) {
        logger.debug { "tool-inventory tool-lost toolId=${event.toolId.value}" }
        repository.recordLost(event.toolId)
    }
}
