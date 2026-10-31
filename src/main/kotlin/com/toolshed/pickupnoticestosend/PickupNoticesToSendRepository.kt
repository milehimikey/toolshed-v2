package com.toolshed.pickupnoticestosend

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate

/**
 * Pickup Notices To Send's own storage (Amendment 5): in-memory, rebuilt from the event stream on restart. Every
 * mutation leaves the store as it was after the first application when repeated (INV-PNS-7).
 */
interface PickupNoticesToSendRepository {
    fun neighborRegistered(
        neighborId: NeighborId,
        name: String,
        email: String,
    )

    fun emailChanged(
        neighborId: NeighborId,
        email: String,
    )

    fun toolAdded(
        toolId: ToolId,
        name: String,
    )

    fun fulfilled(
        holdId: HoldId,
        toolId: ToolId,
        neighborId: NeighborId,
        collectByDate: LocalDate,
    )

    /** The hold's set-aside is over or its email went out; it never comes back (INV-PNS-3, INV-PNS-4). */
    fun end(holdId: HoldId)

    fun checkedOut(
        toolId: ToolId,
        neighborId: NeighborId,
    )

    fun retire(toolId: ToolId)

    fun accountClosed(neighborId: NeighborId)

    /** Every hold on the list right now (INV-PNS-1, INV-PNS-2). */
    fun pickupsToNotify(): List<PickupNoticesToSendRow>
}

private class Pending(
    val holdId: HoldId,
    val toolId: ToolId,
    val neighborId: NeighborId,
    val collectByDate: LocalDate,
)

class InMemoryPickupNoticesToSendRepository : PickupNoticesToSendRepository {
    private val neighborNames = HashMap<NeighborId, String>()
    private val neighborEmails = HashMap<NeighborId, String>()
    private val toolNames = HashMap<ToolId, String>()
    private val pending = LinkedHashMap<HoldId, Pending>()
    private val endedHolds = HashSet<HoldId>()

    @Synchronized
    override fun neighborRegistered(
        neighborId: NeighborId,
        name: String,
        email: String,
    ) {
        neighborNames[neighborId] = name
        neighborEmails[neighborId] = email
    }

    @Synchronized
    override fun emailChanged(
        neighborId: NeighborId,
        email: String,
    ) {
        neighborEmails[neighborId] = email
    }

    @Synchronized
    override fun toolAdded(
        toolId: ToolId,
        name: String,
    ) {
        toolNames[toolId] = name
    }

    @Synchronized
    override fun fulfilled(
        holdId: HoldId,
        toolId: ToolId,
        neighborId: NeighborId,
        collectByDate: LocalDate,
    ) {
        // INV-PNS-3 / INV-PNS-7: a repeat stays one row, and a hold that has left never comes back.
        if (holdId in endedHolds || holdId in pending) return
        pending[holdId] = Pending(holdId, toolId, neighborId, collectByDate)
    }

    @Synchronized
    override fun end(holdId: HoldId) {
        pending.remove(holdId)
        endedHolds.add(holdId)
    }

    @Synchronized
    override fun checkedOut(
        toolId: ToolId,
        neighborId: NeighborId,
    ) {
        // Only the holder's own checkout of the hold's tool ends the set-aside; anyone else's changes nothing.
        endWhere { it.toolId == toolId && it.neighborId == neighborId }
    }

    @Synchronized
    override fun retire(toolId: ToolId) = endWhere { it.toolId == toolId }

    @Synchronized
    override fun accountClosed(neighborId: NeighborId) = endWhere { it.neighborId == neighborId }

    private fun endWhere(matches: (Pending) -> Boolean) {
        pending.values
            .filter(matches)
            .map { it.holdId }
            .forEach(::end)
    }

    @Synchronized
    override fun pickupsToNotify(): List<PickupNoticesToSendRow> =
        pending.values.mapNotNull { p ->
            val name = neighborNames[p.neighborId] ?: return@mapNotNull null
            val email = neighborEmails[p.neighborId] ?: return@mapNotNull null
            val tool = toolNames[p.toolId] ?: return@mapNotNull null
            PickupNoticesToSendRow(p.holdId, p.toolId, p.neighborId, p.collectByDate, name, email, tool)
        }
}

@Configuration
class PickupNoticesToSendRepositoryConfiguration {
    @Bean
    fun pickupNoticesToSendRepository(): PickupNoticesToSendRepository = InMemoryPickupNoticesToSendRepository()
}
