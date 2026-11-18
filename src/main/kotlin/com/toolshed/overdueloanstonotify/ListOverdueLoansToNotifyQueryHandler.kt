package com.toolshed.overdueloanstonotify

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component
import java.time.Clock

/** List loans to notify (INV-OLN-1): overdue once today in the shed's zone is after the due date. */
@Component
class ListOverdueLoansToNotifyQueryHandler(
    private val repository: OverdueLoansToNotifyRepository,
    private val clock: Clock,
) {
    @QueryHandler
    fun handle(query: ListOverdueLoansToNotify): List<OverdueLoanToNotifyRow> =
        repository.overdue(clock.instant().atZone(clock.zone).toLocalDate())
}
