package com.toolshed.closeaccount

import com.toolshed.axon.LoanId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.messaging.eventstreaming.Tag
import java.util.UUID

/** What the close decision needs to know about one neighbor. */
data class NeighborAccountFacts(
    val registered: Boolean,
    val suspended: Boolean,
    val closed: Boolean,
    val openLoans: Set<LoanId>,
)

/**
 * The neighbor's account. Tool Returned and Tool Lost carry only their loan and tool, not the neighbor, so the
 * criteria also reads every return and loss in the shed. That is a wider consistency boundary than the others
 * in the shed, accepted for the first version (deliver.lead ruling); correctness comes first.
 */
@EventSourcedEntity(tagKey = "neighborId")
class NeighborAccountState
    @EntityCreator
    constructor() {
        private var registered = false
        private var suspended = false
        private var closed = false
        private val openLoans = mutableSetOf<LoanId>()

        val facts: NeighborAccountFacts
            get() = NeighborAccountFacts(registered, suspended, closed, openLoans.toSet())

        @EventSourcingHandler
        fun on(event: NeighborRegistered) {
            registered = true
        }

        @EventSourcingHandler
        fun on(event: NeighborSuspended) {
            suspended = true
        }

        @EventSourcingHandler
        fun on(event: NeighborReinstated) {
            suspended = false
        }

        @EventSourcingHandler
        fun on(event: com.toolshed.closeaccount.NeighborAccountClosed) {
            closed = true
        }

        @EventSourcingHandler
        fun on(event: ToolCheckedOut) {
            openLoans.add(event.loanId)
        }

        @EventSourcingHandler
        fun on(event: ToolReturned) {
            openLoans.remove(event.loanId)
        }

        @EventSourcingHandler
        fun on(event: ToolLost) {
            openLoans.remove(event.loanId)
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            fun resolveCriteria(neighborId: UUID): EventCriteria =
                EventCriteria.either(
                    EventCriteria.havingTags(Tag.of("neighborId", neighborId.toString())),
                    EventCriteria.havingAnyTag().andBeingOneOfTypes(
                        ToolReturned::class.java.name,
                        ToolLost::class.java.name,
                    ),
                )
        }
    }
