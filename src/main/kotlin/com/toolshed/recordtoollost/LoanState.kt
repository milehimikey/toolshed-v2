package com.toolshed.recordtoollost

import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.returntool.ToolReturned
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.messaging.eventstreaming.Tag

/** One loan, folded from its own events: whether it was checked out, returned, or already recorded lost. */
@EventSourced(idType = LoanKey::class)
class LoanState
    @EntityCreator
    constructor() {
        private var checkedOutOf: ToolId? = null
        private var isReturned = false
        private var isLost = false

        /** The tool the loan is out on, or null if no Tool Checked Out was recorded for this loan. */
        fun toolId(): ToolId? = checkedOutOf

        fun returned(): Boolean = isReturned

        fun lost(): Boolean = isLost

        @EventSourcingHandler
        fun evolve(event: ToolCheckedOut) {
            checkedOutOf = event.toolId
        }

        @EventSourcingHandler
        fun evolve(event: ToolReturned) {
            isReturned = true
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            isLost = true
        }

        companion object {
            /** The loan's own events, by its tag. */
            @EventCriteriaBuilder
            @JvmStatic
            fun resolveCriteria(key: LoanKey): EventCriteria =
                EventCriteria
                    .havingTags(Tag.of("loanId", key.loanId.value.toString()))
                    .andBeingOneOfTypes(
                        ToolCheckedOut::class.java.name,
                        ToolReturned::class.java.name,
                        ToolLost::class.java.name,
                    )
        }
    }
