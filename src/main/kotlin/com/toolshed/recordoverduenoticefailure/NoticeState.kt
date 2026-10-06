package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.LoanId
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.returntool.ToolReturned
import com.toolshed.sendoverduenotice.OverdueNoticeSent
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria

/**
 * Folded from the notice, failure, return and loss events. Mutable collections, because the shed's history is
 * long and copying the whole state on every event would be wasteful.
 */
@EventSourced(idType = NoticeLedger::class)
class NoticeState
    @EntityCreator
    constructor() {
        private val sent = mutableSetOf<LoanId>()
        private val failures = mutableMapOf<LoanId, String>()
        private val ended = mutableSetOf<LoanId>()

        fun wasNoticeSent(loanId: LoanId): Boolean = loanId in sent

        fun failureReason(loanId: LoanId): String? = failures[loanId]

        fun hasEnded(loanId: LoanId): Boolean = loanId in ended

        @EventSourcingHandler
        fun evolve(event: OverdueNoticeSent) {
            sent.add(event.loanId)
        }

        @EventSourcingHandler
        fun evolve(event: OverdueNoticeFailed) {
            failures[event.loanId] = event.reason
        }

        @EventSourcingHandler
        fun evolve(event: ToolReturned) {
            ended.add(event.loanId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            ended.add(event.loanId)
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            private fun resolveCriteria(ledger: NoticeLedger): EventCriteria =
                EventCriteria
                    .havingAnyTag()
                    .andBeingOneOfTypes(
                        OverdueNoticeSent::class.java.name,
                        OverdueNoticeFailed::class.java.name,
                        ToolReturned::class.java.name,
                        ToolLost::class.java.name,
                    )
        }
    }
