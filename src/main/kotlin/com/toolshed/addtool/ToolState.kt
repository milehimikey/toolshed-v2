package com.toolshed.addtool

import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator

/** The Tool Added fact for one tool, if it was ever added. Retirements and loans are other slices' events. */
@EventSourcedEntity(tagKey = "toolId")
class ToolState
    @EntityCreator
    constructor() {
        private var current: ToolAdded? = null

        val added: ToolAdded?
            get() = current

        @EventSourcingHandler
        fun evolve(event: ToolAdded) {
            current = event
        }
    }
