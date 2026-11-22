package com.toolshed.retiretool

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.ToolId
import com.toolshed.recordtoollost.ToolLost
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.eventstreaming.EventCriteria

/**
 * Folded from the tool events the retirement decision reads. A lost tool is retired by Tool Lost, so Tool Lost
 * counts as a retirement. Mutable sets, because the shed's history is long and copying the state on every event
 * would be wasteful.
 */
@EventSourced(idType = ToolLedger::class)
class ToolState
    @EntityCreator
    constructor() {
        private val tools = mutableSetOf<ToolId>()
        private val retired = mutableSetOf<ToolId>()

        fun hasTool(toolId: ToolId): Boolean = toolId in tools

        fun isRetired(toolId: ToolId): Boolean = toolId in retired

        @EventSourcingHandler
        fun evolve(event: ToolAdded) {
            tools.add(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolRetired) {
            retired.add(event.toolId)
        }

        @EventSourcingHandler
        fun evolve(event: ToolLost) {
            retired.add(event.toolId)
        }

        companion object {
            @EventCriteriaBuilder
            @JvmStatic
            private fun resolveCriteria(ledger: ToolLedger): EventCriteria =
                EventCriteria
                    .havingAnyTag()
                    .andBeingOneOfTypes(
                        ToolAdded::class.java.name,
                        ToolRetired::class.java.name,
                        ToolLost::class.java.name,
                    )
        }
    }
