package com.toolshed.myloans

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component
import java.time.Clock

/** Answers My Loans for the one neighbor the query names. Overdue is worked out here, against today in the shed's zone (INV-MYL-4). */
@Component
class MyLoansQueryHandler(
    private val repository: MyLoansRepository,
    private val clock: Clock,
) {
    @QueryHandler
    fun handle(query: GetMyLoans): MyLoans = MyLoans(repository.rows(query.neighborId, clock.instant().atZone(clock.zone).toLocalDate()))
}
