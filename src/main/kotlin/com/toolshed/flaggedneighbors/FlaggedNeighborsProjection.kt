package com.toolshed.flaggedneighbors

import com.toolshed.addtool.ToolAdded
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.extendloan.LoanExtended
import com.toolshed.flagneighbor.NeighborFlagged
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Flagged Neighbors (INV-FLN-1): applies only the events the doc lists, each idempotently (INV-FLN-9). A flag
 * creates or extends the neighbor's entry (INV-FLN-3); a suspension or account closure removes it (INV-FLN-5, 6).
 */
@Component
class FlaggedNeighborsProjection(
    private val repository: FlaggedNeighborsRepository,
) {
    @EventHandler
    fun on(event: ToolAdded) {
        repository.saveToolName(event.toolId, event.name)
        logger.debug { "flagged-neighbors toolId=${event.toolId.value} outcome=tool-name-recorded" }
    }

    @EventHandler
    fun on(event: NeighborRegistered) {
        repository.saveNeighbor(event.neighborId, event.name, event.email)
        logger.debug { "flagged-neighbors neighborId=${event.neighborId.value} outcome=neighbor-recorded" }
    }

    @EventHandler
    fun on(event: NeighborEmailChanged) {
        repository.changeEmail(event.neighborId, event.email)
        logger.debug { "flagged-neighbors neighborId=${event.neighborId.value} outcome=email-changed" }
    }

    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.checkedOut(event.loanId, LoanFacts(event.toolId, event.neighborId, event.dueDate))
        logger.debug { "flagged-neighbors loanId=${event.loanId.value} outcome=loan-recorded" }
    }

    @EventHandler
    fun on(event: LoanExtended) {
        repository.extendLoan(event.loanId, event.dueDate)
        logger.debug { "flagged-neighbors loanId=${event.loanId.value} outcome=due-date-moved" }
    }

    @EventHandler
    fun on(event: ToolReturned) {
        repository.returned(event.loanId, event.returnedOn)
        logger.debug { "flagged-neighbors loanId=${event.loanId.value} outcome=return-recorded" }
    }

    @EventHandler
    fun on(event: NeighborFlagged) {
        repository.flag(event.neighborId, event.loanIds, event.flaggedAt)
        logger.debug { "flagged-neighbors neighborId=${event.neighborId.value} outcome=flagged" }
    }

    @EventHandler
    fun on(event: NeighborSuspended) {
        repository.remove(event.neighborId)
        logger.debug { "flagged-neighbors neighborId=${event.neighborId.value} outcome=removed reason=suspended" }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.remove(event.neighborId)
        logger.debug { "flagged-neighbors neighborId=${event.neighborId.value} outcome=removed reason=account-closed" }
    }
}
