package com.toolshed.overdueloanstonotify

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component
import java.time.Clock

/** List loans to notify (INV-OLN-1): due once today in the shed's zone is after the loan's due date. */
@Component
class OverdueLoansToNotifyQueryHandler(
    private val repository: OverdueLoansToNotifyRepository,
    private val clock: Clock,
) {
    @QueryHandler
    fun handle(query: ListOverdueLoansToNotify): List<OverdueLoanToNotifyRow> =
        repository.overdue(clock.instant().atZone(clock.zone).toLocalDate())
}
