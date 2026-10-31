package com.toolshed.pickupstoexpire

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component
import java.time.Clock

/** List set-asides to expire (INV-PTE-2): due once today in the shed's zone is after the collect-by date. */
@Component
class ListPickupsToExpireQueryHandler(
    private val repository: PickupsToExpireRepository,
    private val clock: Clock,
) {
    @QueryHandler
    fun handle(query: ListPickupsToExpire): List<PickupToExpireRow> =
        repository.dueToExpire(clock.instant().atZone(clock.zone).toLocalDate())
}
