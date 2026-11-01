package com.toolshed.sendoverduenotice

import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.returntool.ToolReturned
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.messaging.eventstreaming.Tag
import java.time.LocalDate

/**
 * One loan's overdue-email state, folded from the loan's own events: its latest due date (Tool Checked Out, Loan Extended),
 * whether it has ended (Tool Returned, Tool Lost), and the notice already recorded, if any.
 */
@EventSourced(idType = OverdueNoticeKey::class)
class SendOverdueNoticeState
    @EntityCreator
    constructor() {
        private var due: LocalDate? = null
        private var ended = false
        private var sent: OverdueNoticeSent? = null

        /** INV-OVN-10: Tool Checked Out has been recorded for the loan. */
        fun exists(): Boolean = due != null

        /** The Overdue Notice Sent already recorded for the loan, if any (INV-OVN-2). */
        fun noticeSent(): OverdueNoticeSent? = sent

        /** INV-OVN-5: the tool was returned or recorded lost. */
        fun loanEnded(): Boolean = ended

        /** The loan's current due date: the latest set by Tool Checked Out or Loan Extended (INV-OVN-6, INV-OVN-11). */
        fun dueDate(): LocalDate? = due

        @EventSourcingHandler
        fun evolve(event: ToolCheckedOut) {
            if (due == null) due = event.dueDate
        }

        @EventSourcingHandler
        fun evolve(event: LoanExtended) {
            due = event.dueDate
        }

        @EventSourcingHandler
        fun evolve(
            @Suppress("UNUSED_PARAMETER") event: ToolReturned,
        ) {
            ended = true
        }

        @EventSourcingHandler
        fun evolve(
            @Suppress("UNUSED_PARAMETER") event: ToolLost,
        ) {
            ended = true
        }

        @EventSourcingHandler
        fun evolve(event: OverdueNoticeSent) {
            if (sent == null) sent = event
        }

        companion object {
            /** The events this decision reads: all carry the loan's id tag. */
            @EventCriteriaBuilder
            @JvmStatic
            fun resolveCriteria(key: OverdueNoticeKey): EventCriteria =
                EventCriteria
                    .havingTags(Tag.of("loanId", key.loanId.value.toString()))
                    .andBeingOneOfTypes(
                        ToolCheckedOut::class.java.name,
                        LoanExtended::class.java.name,
                        ToolReturned::class.java.name,
                        ToolLost::class.java.name,
                        OverdueNoticeSent::class.java.name,
                    )
        }
    }
