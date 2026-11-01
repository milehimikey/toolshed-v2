package com.toolshed.overdueloanstonotify

import com.toolshed.addtool.ToolAdded
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.returntool.ToolReturned
import com.toolshed.sendoverduenotice.OverdueNoticeSent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Overdue Loans To Notify: one row per open loan (INV-OLN-1), removed when its notice is sent, the tool is returned or
 * recorded lost (INV-OLN-8), each event applied idempotently (INV-OLN-9). Whether a row is overdue is never stored;
 * the query works it out from today. Tool Retired needs no handler: a retired tool's loan stays on (INV-OLN-4).
 */
@Component
class OverdueLoansToNotifyProjection(
    private val repository: OverdueLoansToNotifyRepository,
) {
    @EventHandler
    fun on(event: ToolAdded) {
        repository.toolAdded(event.toolId, event.name)
        logger.debug { "overdue-loans-to-notify toolId=${event.toolId.value} outcome=tool-name-kept" }
    }

    @EventHandler
    fun on(event: NeighborRegistered) {
        repository.neighborRegistered(event.neighborId, event.name, event.email)
        logger.debug { "overdue-loans-to-notify neighborId=${event.neighborId.value} outcome=neighbor-kept" }
    }

    @EventHandler
    fun on(event: NeighborEmailChanged) {
        repository.emailChanged(event.neighborId, event.email)
        logger.debug { "overdue-loans-to-notify neighborId=${event.neighborId.value} outcome=email-replaced" }
    }

    @EventHandler
    fun on(event: ToolCheckedOut) {
        repository.checkedOut(event.loanId, event.toolId, event.neighborId, event.dueDate)
        logger.debug { "overdue-loans-to-notify loanId=${event.loanId.value} outcome=open" }
    }

    @EventHandler
    fun on(event: LoanExtended) {
        repository.extended(event.loanId, event.dueDate)
        logger.debug { "overdue-loans-to-notify loanId=${event.loanId.value} outcome=due-date-moved" }
    }

    @EventHandler
    fun on(event: OverdueNoticeSent) {
        repository.end(event.loanId)
        logger.debug { "overdue-loans-to-notify loanId=${event.loanId.value} outcome=ended reason=notice-sent" }
    }

    @EventHandler
    fun on(event: ToolReturned) {
        repository.end(event.loanId)
        logger.debug { "overdue-loans-to-notify loanId=${event.loanId.value} outcome=ended reason=returned" }
    }

    @EventHandler
    fun on(event: ToolLost) {
        repository.end(event.loanId)
        logger.debug { "overdue-loans-to-notify loanId=${event.loanId.value} outcome=ended reason=lost" }
    }
}
