package com.toolshed.myloans

import com.toolshed.addtool.ToolAdded
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.extendloan.LoanExtended
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordoverduenoticefailure.OverdueNoticeFailed
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.sendoverduenotice.OverdueNoticeSent
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

/** Applies the events My Loans reads (its alsoReads, INV-MYL-8). Every handler is idempotent (INV-MYL-11). */
@Component
class MyLoansProjection(
    private val repository: MyLoansRepository,
) {
    @EventHandler
    fun on(event: ToolAdded) {
        repository.addTool(event.toolId, event.name)
    }

    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.checkOut(event.loanId, event.toolId, event.neighborId, event.checkedOutAt, event.dueDate)
    }

    @EventHandler
    fun on(event: LoanExtended) {
        repository.extend(event.loanId, event.dueDate)
    }

    @EventHandler
    fun on(event: OverdueNoticeSent) {
        repository.noticeSent(event.loanId)
    }

    @EventHandler
    fun on(event: OverdueNoticeFailed) {
        repository.noticeFailed(event.loanId)
    }

    @EventHandler
    fun on(event: ToolReturned) {
        repository.endLoan(event.loanId)
    }

    @EventHandler
    fun on(event: ToolLost) {
        repository.recordLost(event.loanId, event.toolId)
    }

    @EventHandler
    fun on(event: HoldPlaced) {
        repository.placeHold(event.holdId, event.toolId, event.neighborId)
    }

    @EventHandler
    fun on(event: HoldCancelled) {
        repository.endHold(event.holdId)
    }

    @EventHandler
    fun on(event: HoldExpired) {
        repository.endHold(event.holdId)
    }

    @EventHandler
    fun on(event: ToolRetired) {
        repository.retire(event.toolId)
    }

    @EventHandler
    fun on(event: NeighborAccountClosed) {
        repository.closeAccount(event.neighborId)
    }
}
