package com.toolshed.scheduletoolmaintenance

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.completetoolmaintenance.ToolMaintenanceCompleted
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria

/** The work a tool in maintenance currently needs: the latest tasks and notes. */
internal data class Work(
    val tasks: List<String>,
    val notes: String?,
)

/**
 * Folded from every event the scheduling decision reads, as one shed-wide ledger. Hold events other than Placed and
 * Fulfilled carry no tool, so a hold is followed to its tool through those two (see the slice doc's Dependencies).
 */
@EventSourced(idType = MaintenanceLedger::class)
class ScheduleMaintenanceState
    @EntityCreator
    constructor() {
        private val toolNames = mutableMapOf<ToolId, String>()
        private val retired = mutableSetOf<ToolId>()
        private val openLoans = mutableMapOf<LoanId, ToolId>()
        private val holdTools = mutableMapOf<HoldId, ToolId>()
        private val holdHolders = mutableMapOf<HoldId, NeighborId>()
        private val waitingHolds = mutableSetOf<HoldId>()
        private val setAsideHolds = mutableSetOf<HoldId>()
        private val inMaintenance = mutableMapOf<ToolId, Work>()

        internal fun hasTool(toolId: ToolId): Boolean = toolId in toolNames

        internal fun toolName(toolId: ToolId): String? = toolNames[toolId]

        /** Retired by Tool Retired or Tool Lost; a retired tool that was in maintenance is retired, not in maintenance. */
        internal fun isRetired(toolId: ToolId): Boolean = toolId in retired

        internal fun isOnLoan(toolId: ToolId): Boolean = openLoans.containsValue(toolId)

        internal fun isSetAside(toolId: ToolId): Boolean = setAsideHolds.any { holdTools[it] == toolId }

        internal fun hasWaitingHolds(toolId: ToolId): Boolean = waitingHolds.any { holdTools[it] == toolId }

        /** The current work when the tool is in maintenance (Scheduled and not yet Completed), else null. */
        internal fun currentWork(toolId: ToolId): Work? = inMaintenance[toolId]

        @EventSourcingHandler
        fun evolve(event: ToolAdded) {
            toolNames[event.toolId] = event.name
        }

        @EventSourcingHandler
        fun evolve(event: ToolRetired) {
            retired += event.toolId
            inMaintenance.remove(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            retired += event.toolId
            inMaintenance.remove(event.toolId)
            openLoans.remove(event.loanId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolCheckedOut) {
            openLoans[event.loanId] = event.toolId
            // A holder who checks out the tool they hold ends that hold, as in Check Out Tool (INV-CHK-9).
            holdHolders
                .filter { (holdId, holder) -> holder == event.neighborId && holdTools[holdId] == event.toolId }
                .keys
                .forEach { end(it) }
        }

        @EventSourcingHandler
        fun evolve(event: ToolReturned) {
            openLoans.remove(event.loanId)
        }

        @EventSourcingHandler
        fun evolve(event: HoldPlaced) {
            holdTools[event.holdId] = event.toolId
            holdHolders[event.holdId] = event.neighborId
            waitingHolds += event.holdId
        }

        @EventSourcingHandler
        fun evolve(event: HoldFulfilled) {
            holdTools[event.holdId] = event.toolId
            waitingHolds -= event.holdId
            setAsideHolds += event.holdId
        }

        @EventSourcingHandler
        fun evolve(event: HoldCancelled) = end(event.holdId)

        @EventSourcingHandler
        fun evolve(event: HoldExpired) = end(event.holdId)

        @EventSourcingHandler
        fun evolve(event: ToolMaintenanceScheduled) {
            inMaintenance[event.toolId] = Work(event.tasks, event.notes)
        }

        @EventSourcingHandler
        fun evolve(event: ToolMaintenanceTasksChanged) {
            inMaintenance[event.toolId] = Work(event.tasks, event.notes)
        }

        @EventSourcingHandler
        fun evolve(event: ToolMaintenanceCompleted) {
            inMaintenance.remove(event.toolId)
        }

        private fun end(holdId: HoldId) {
            waitingHolds -= holdId
            setAsideHolds -= holdId
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            private fun resolveCriteria(ledger: MaintenanceLedger): EventCriteria =
                EventCriteria
                    .havingAnyTag()
                    .andBeingOneOfTypes(
                        ToolAdded::class.java.name,
                        ToolRetired::class.java.name,
                        ToolLost::class.java.name,
                        ToolCheckedOut::class.java.name,
                        ToolReturned::class.java.name,
                        HoldPlaced::class.java.name,
                        HoldFulfilled::class.java.name,
                        HoldCancelled::class.java.name,
                        HoldExpired::class.java.name,
                        ToolMaintenanceScheduled::class.java.name,
                        ToolMaintenanceTasksChanged::class.java.name,
                        ToolMaintenanceCompleted::class.java.name,
                    )
        }
    }
