package com.toolshed.completetoolmaintenance

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.ToolId
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceScheduled
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria

/**
 * Folded from the tool events the completion decision reads. A tool is in maintenance from its Tool Maintenance
 * Scheduled until the next Tool Maintenance Completed. Tool Maintenance Tasks Changed leaves the tool in maintenance,
 * so it is not read. Tool Retired and Tool Lost retire the tool. A shed-wide ledger, coarser than the doc's per-tool
 * rule and never looser (the house idiom).
 */
@EventSourced(idType = MaintenanceLedger::class)
class MaintenanceState
    @EntityCreator
    constructor() {
        private val tools = mutableSetOf<ToolId>()
        private val retired = mutableSetOf<ToolId>()
        private val inMaintenance = mutableSetOf<ToolId>()

        fun hasTool(toolId: ToolId): Boolean = toolId in tools

        fun isRetired(toolId: ToolId): Boolean = toolId in retired

        fun isInMaintenance(toolId: ToolId): Boolean = toolId in inMaintenance

        @EventSourcingHandler
        fun evolve(event: ToolAdded) {
            tools.add(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolMaintenanceScheduled) {
            inMaintenance.add(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolMaintenanceCompleted) {
            inMaintenance.remove(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolRetired) {
            retired.add(event.toolId)
            inMaintenance.remove(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            retired.add(event.toolId)
            inMaintenance.remove(event.toolId)
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            private fun resolveCriteria(ledger: MaintenanceLedger): EventCriteria =
                EventCriteria
                    .havingAnyTag()
                    .andBeingOneOfTypes(
                        ToolAdded::class.java.name,
                        ToolMaintenanceScheduled::class.java.name,
                        ToolMaintenanceCompleted::class.java.name,
                        ToolRetired::class.java.name,
                        ToolLost::class.java.name,
                    )
        }
    }
