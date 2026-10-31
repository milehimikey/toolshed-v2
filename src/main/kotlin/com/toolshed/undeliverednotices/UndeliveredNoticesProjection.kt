package com.toolshed.undeliverednotices

import com.toolshed.addtool.ToolAdded
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.extendloan.LoanExtended
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.recordoverduenoticefailure.OverdueNoticeFailed
import com.toolshed.recordpickupnoticefailure.PickupNoticeFailed
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Undelivered Notices (INV-UDN-1): applies only the events the doc lists, each idempotently (INV-UDN-8). A
 * checkout ends a pickup entry only for its own holder (INV-UDN-5), and retiring a tool ends set-asides but never
 * an open loan (INV-UDN-4).
 */
@Component
class UndeliveredNoticesProjection(
    private val repository: UndeliveredNoticesRepository,
) {
    @EventHandler
    fun on(event: ToolAdded) {
        repository.saveToolName(event.toolId, event.name)
        logger.debug { "undelivered-notices toolId=${event.toolId.value} outcome=tool-name-recorded" }
    }

    @EventHandler
    fun on(event: NeighborRegistered) {
        repository.saveNeighbor(event.neighborId, event.name, event.email)
        logger.debug { "undelivered-notices neighborId=${event.neighborId.value} outcome=neighbor-recorded" }
    }

    @EventHandler
    fun on(event: NeighborEmailChanged) {
        repository.changeEmail(event.neighborId, event.email)
        logger.debug { "undelivered-notices neighborId=${event.neighborId.value} outcome=email-changed" }
    }

    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.openLoan(event.loanId, OpenLoan(event.toolId, event.neighborId, event.dueDate))
        repository.endSetAsidesForToolAndNeighbor(event.toolId, event.neighborId)
        logger.debug { "undelivered-notices loanId=${event.loanId.value} outcome=loan-opened" }
    }

    @EventHandler
    fun on(event: LoanExtended) {
        repository.extendLoan(event.loanId, event.dueDate)
        logger.debug { "undelivered-notices loanId=${event.loanId.value} outcome=due-date-moved" }
    }

    @EventHandler
    fun on(event: ToolReturned) {
        repository.endLoan(event.loanId)
        logger.debug { "undelivered-notices loanId=${event.loanId.value} outcome=ended reason=returned" }
    }

    @EventHandler
    fun on(event: ToolLost) {
        repository.endLoan(event.loanId)
        logger.debug { "undelivered-notices loanId=${event.loanId.value} outcome=ended reason=lost" }
    }

    @EventHandler
    fun on(event: HoldFulfilled) {
        repository.setAside(event.holdId, SetAside(event.toolId, event.neighborId, event.collectByDate))
        logger.debug { "undelivered-notices holdId=${event.holdId.value} outcome=set-aside" }
    }

    @EventHandler
    fun on(event: HoldCancelled) {
        repository.endSetAside(event.holdId)
        logger.debug { "undelivered-notices holdId=${event.holdId.value} outcome=ended reason=cancelled" }
    }

    @EventHandler
    fun on(event: HoldExpired) {
        repository.endSetAside(event.holdId)
        logger.debug { "undelivered-notices holdId=${event.holdId.value} outcome=ended reason=expired" }
    }

    @EventHandler
    fun on(event: ToolRetired) {
        repository.endSetAsidesOnTool(event.toolId)
        logger.debug { "undelivered-notices toolId=${event.toolId.value} outcome=set-asides-ended reason=retired" }
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.endSetAsidesForNeighbor(event.neighborId)
        logger.debug { "undelivered-notices neighborId=${event.neighborId.value} outcome=set-asides-ended reason=account-closed" }
    }

    @EventHandler
    fun on(event: OverdueNoticeFailed) {
        repository.overdueFailed(event.loanId, event.reason, event.failedAt)
        logger.debug { "undelivered-notices loanId=${event.loanId.value} outcome=overdue-notice-failed" }
    }

    @EventHandler
    fun on(event: PickupNoticeFailed) {
        repository.pickupFailed(event.holdId, event.reason, event.failedAt)
        logger.debug { "undelivered-notices holdId=${event.holdId.value} outcome=pickup-notice-failed" }
    }
}
