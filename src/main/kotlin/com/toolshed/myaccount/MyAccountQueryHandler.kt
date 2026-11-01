package com.toolshed.myaccount

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/** Answers My Account for the one neighbor the query names, never another (INV-MYAC-1). */
@Component
class MyAccountQueryHandler(
    private val repository: MyAccountRepository,
) {
    @QueryHandler
    fun handle(query: GetMyAccount): MyAccount = MyAccount(repository.find(query.neighborId))
}
