package com.toolshed.myholds

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * My Holds' own storage (Amendment 5): in-memory, rebuilt from the event stream on restart. Every mutation is
 * written so that applying the same event again leaves the store as it was after the first (INV-MYHD-15).
 */
interface MyHoldsRepository {
    fun saveToolName(
        toolId: ToolId,
        name: String,
    )

    fun toolName(toolId: ToolId): String?

    fun place(
        holdId: HoldId,
        toolId: ToolId,
        neighborId: NeighborId,
        placedAt: Instant,
    )

    fun fulfill(
        holdId: HoldId,
        collectByDate: LocalDate,
    )

    fun noticeSent(holdId: HoldId)

    fun noticeFailed(holdId: HoldId)

    fun end(holdId: HoldId)

    fun endAllOnTool(toolId: ToolId)

    fun endForToolAndNeighbor(
        toolId: ToolId,
        neighborId: NeighborId,
    )

    fun endAllForNeighbor(neighborId: NeighborId)

    /** Live holds for [neighborId] (INV-MYHD-1): nothing about any other neighbor's holds. */
    fun holdsForNeighbor(neighborId: NeighborId): List<HoldEntry>

    /** Live holds on [toolId], in the order Hold Placed was recorded (INV-MYHD-2), for working out position. */
    fun holdsOnTool(toolId: ToolId): List<HoldEntry>
}

class InMemoryMyHoldsRepository : MyHoldsRepository {
    private val toolNames = ConcurrentHashMap<ToolId, String>()
    private val holds = ConcurrentHashMap<HoldId, HoldEntry>()
    private val sequence = AtomicLong(0)

    override fun saveToolName(
        toolId: ToolId,
        name: String,
    ) {
        toolNames[toolId] = name
    }

    override fun toolName(toolId: ToolId): String? = toolNames[toolId]

    override fun place(
        holdId: HoldId,
        toolId: ToolId,
        neighborId: NeighborId,
        placedAt: Instant,
    ) {
        // INV-MYHD-15: a repeat of the same Hold Placed must not claim a second place in the queue.
        if (holds.containsKey(holdId)) return
        holds[holdId] = HoldEntry(holdId, toolId, neighborId, placedAt, sequence.incrementAndGet())
    }

    override fun fulfill(
        holdId: HoldId,
        collectByDate: LocalDate,
    ) {
        holds.computeIfPresent(holdId) { _, hold -> hold.copy(fulfilled = true, collectByDate = collectByDate) }
    }

    override fun noticeSent(holdId: HoldId) {
        holds.computeIfPresent(holdId) { _, hold -> hold.copy(pickupNotice = PickupNotice.SENT) }
    }

    override fun noticeFailed(holdId: HoldId) {
        holds.computeIfPresent(holdId) { _, hold -> hold.copy(pickupNotice = PickupNotice.UNDELIVERED) }
    }

    override fun end(holdId: HoldId) {
        holds.remove(holdId)
    }

    override fun endAllOnTool(toolId: ToolId) {
        removeWhere { it.toolId == toolId }
    }

    override fun endForToolAndNeighbor(
        toolId: ToolId,
        neighborId: NeighborId,
    ) {
        removeWhere { it.toolId == toolId && it.neighborId == neighborId }
    }

    override fun endAllForNeighbor(neighborId: NeighborId) {
        removeWhere { it.neighborId == neighborId }
    }

    override fun holdsForNeighbor(neighborId: NeighborId): List<HoldEntry> = holds.values.filter { it.neighborId == neighborId }

    override fun holdsOnTool(toolId: ToolId): List<HoldEntry> = holds.values.filter { it.toolId == toolId }.sortedBy { it.sequence }

    private fun removeWhere(matches: (HoldEntry) -> Boolean) {
        holds.values.filter(matches).forEach { holds.remove(it.holdId) }
    }
}

@Configuration
class MyHoldsRepositoryConfiguration {
    @Bean
    fun myHoldsRepository(): MyHoldsRepository = InMemoryMyHoldsRepository()
}
