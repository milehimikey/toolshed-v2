package com.toolshed.myaccount

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/** Answers My Account from the view, for the one neighbor the query names. */
@Component
class MyAccountQueryHandler(
    private val view: MyAccountView,
) {
    @QueryHandler
    fun get(query: GetMyAccount): MyAccountLookup = MyAccountLookup(view.get(query.neighborId))
}
