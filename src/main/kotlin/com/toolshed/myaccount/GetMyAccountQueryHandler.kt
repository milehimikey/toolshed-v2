package com.toolshed.myaccount

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/**
 * Get my account (INV-MYAC-1): the one account whose neighbor ID it is given, as a list of zero (not found) or one.
 * Status and the offers are worked out from the facts, in the doc's order: closed, then suspended, then active.
 */
@Component
class GetMyAccountQueryHandler(
    private val repository: MyAccountRepository,
) {
    @QueryHandler
    fun handle(query: GetMyAccount): List<MyAccountRow> = listOfNotNull(repository.find(query.neighborId)?.let(::toRow))

    private fun toRow(account: AccountEntry): MyAccountRow {
        val status =
            when {
                account.closed -> AccountStatus.CLOSED
                account.suspended -> AccountStatus.SUSPENDED
                else -> AccountStatus.ACTIVE
            }
        // INV-MYAC-9 and INV-MYAC-11: both are offered on any account that isn't closed, suspended included.
        val offered = status != AccountStatus.CLOSED
        return MyAccountRow(account.neighborId, account.name, account.email, status, offered, offered)
    }
}
