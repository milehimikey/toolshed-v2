package com.toolshed.returntool

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.recordtoollost.ToolLost
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria
import java.time.LocalDate

/** A loan as far as a return cares: whose it is, what tool, when it is due, and whether it has ended. */
data class Loan(
    val loanId: LoanId,
    val toolId: ToolId,
    val borrower: NeighborId,
    val dueDate: LocalDate,
    val returned: Boolean,
    val lost: Boolean,
)

/**
 * Folded from the checkout, extension, return and loss events. Mutable map, because the shed's history is long
 * and copying the whole state on every event would be wasteful.
 */
@EventSourced(idType = LoanBook::class)
class LoanBookState
    @EntityCreator
    constructor() {
        private val loans = mutableMapOf<LoanId, Loan>()

        fun loan(loanId: LoanId): Loan? = loans[loanId]

        @EventSourcingHandler
        fun evolve(event: ToolCheckedOut) {
            loans[event.loanId] = Loan(event.loanId, event.toolId, event.neighborId, event.dueDate, returned = false, lost = false)
        }

        @EventSourcingHandler
        fun evolve(event: LoanExtended) {
            loans[event.loanId]?.let { loans[event.loanId] = it.copy(dueDate = event.dueDate) }
        }

        @EventSourcingHandler
        fun evolve(event: ToolReturned) {
            loans[event.loanId]?.let { loans[event.loanId] = it.copy(returned = true) }
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            loans[event.loanId]?.let { loans[event.loanId] = it.copy(lost = true) }
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            private fun resolveCriteria(book: LoanBook): EventCriteria =
                EventCriteria
                    .havingAnyTag()
                    .andBeingOneOfTypes(
                        ToolCheckedOut::class.java.name,
                        LoanExtended::class.java.name,
                        ToolReturned::class.java.name,
                        ToolLost::class.java.name,
                    )
        }
    }
