package com.toolshed.pickupnoticestosend

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/** List pickups to notify (INV-PNS-1, INV-PNS-2): every hold set aside whose pickup email hasn't gone out. Internal; no caller identity. */
@Component
class ListPickupsToNotifyQueryHandler(
    private val repository: PickupNoticesToSendRepository,
) {
    @QueryHandler
    fun handle(query: ListPickupsToNotify): List<PickupNoticesToSendRow> = repository.pickupsToNotify()
}
