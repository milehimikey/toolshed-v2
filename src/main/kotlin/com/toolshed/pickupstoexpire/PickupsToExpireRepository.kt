package com.toolshed.pickupstoexpire

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate

/**
 * Pickups To Expire's own storage (Amendment 5): in-memory, rebuilt from the event stream on restart. Every mutation
 * leaves the store as it was after the first application when repeated (INV-PTE-10).
 */
interface PickupsToExpireRepository {
    fun setAside(
        holdId: HoldId,
        toolId: ToolId,
        neighborId: NeighborId,
        collectByDate: LocalDate,
    )

    fun checkedOut(
        toolId: ToolId,
        neighborId: NeighborId,
    )

    fun end(holdId: HoldId)

    fun retire(toolId: ToolId)

    fun endAllForNeighbor(neighborId: NeighborId)

    /** Every row whose collect-by date is before [today] (INV-PTE-2). */
    fun dueToExpire(today: LocalDate): List<PickupToExpireRow>
}

class InMemoryPickupsToExpireRepository : PickupsToExpireRepository {
    private val rows = LinkedHashMap<HoldId, PickupToExpireRow>()

    /** Nothing puts a row back, so a replayed Hold Fulfilled for a hold that has left must not revive it. */
    private val ended = HashSet<HoldId>()

    @Synchronized
    override fun setAside(
        holdId: HoldId,
        toolId: ToolId,
        neighborId: NeighborId,
        collectByDate: LocalDate,
    ) {
        if (holdId in ended || holdId in rows) return
        rows[holdId] = PickupToExpireRow(holdId, toolId, neighborId, collectByDate)
    }

    @Synchronized
    override fun checkedOut(
        toolId: ToolId,
        neighborId: NeighborId,
    ) = removeWhere { it.toolId == toolId && it.neighborId == neighborId }

    @Synchronized
    override fun end(holdId: HoldId) = removeWhere { it.holdId == holdId }

    @Synchronized
    override fun retire(toolId: ToolId) = removeWhere { it.toolId == toolId }

    @Synchronized
    override fun endAllForNeighbor(neighborId: NeighborId) = removeWhere { it.neighborId == neighborId }

    @Synchronized
    override fun dueToExpire(today: LocalDate): List<PickupToExpireRow> = rows.values.filter { today.isAfter(it.collectByDate) }

    private fun removeWhere(predicate: (PickupToExpireRow) -> Boolean) {
        val gone = rows.values.filter(predicate).map { it.holdId }
        gone.forEach {
            rows.remove(it)
            ended.add(it)
        }
    }
}

@Configuration
class PickupsToExpireRepositoryConfiguration {
    @Bean
    fun pickupsToExpireRepository(): PickupsToExpireRepository = InMemoryPickupsToExpireRepository()
}
