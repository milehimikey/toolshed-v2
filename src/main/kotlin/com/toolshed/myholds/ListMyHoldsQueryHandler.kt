package com.toolshed.myholds

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/**
 * List my holds (INV-MYHD-1): every live hold of the asking neighbor, with position and status worked out now,
 * from current facts, never stored (INV-MYHD-17). Listed by tool name, ignoring case, then placement order
 * (INV-MYHD-18).
 */
@Component
class ListMyHoldsQueryHandler(
    private val repository: MyHoldsRepository,
    private val clock: Clock,
) {
    @QueryHandler
    fun handle(query: ListMyHolds): List<MyHoldsRow> {
        val today = LocalDate.now(clock)
        return repository
            .holdsForNeighbor(query.neighborId)
            .sortedWith(compareBy({ toolNameOf(it).lowercase() }, { it.sequence }))
            .map { toRow(it, today) }
    }

    private fun toolNameOf(hold: HoldEntry): String = repository.toolName(hold.toolId).orEmpty()

    private fun toRow(
        hold: HoldEntry,
        today: LocalDate,
    ): MyHoldsRow =
        MyHoldsRow(
            holdId = hold.holdId,
            neighborId = hold.neighborId,
            toolId = hold.toolId,
            toolName = toolNameOf(hold),
            placedAt = hold.placedAt,
            status = status(hold, today),
            position = if (hold.fulfilled) null else position(hold),
            collectByDate = hold.collectByDate,
            pickupNotice = hold.pickupNotice,
            canCancel = true,
        )

    /** INV-MYHD-2: 1 plus the number of this tool's waiting holds placed before this one. */
    private fun position(hold: HoldEntry): Int =
        1 + repository.holdsOnTool(hold.toolId).count { !it.fulfilled && it.sequence < hold.sequence }

    /** The status rules, in the doc's order: the first that holds wins. */
    private fun status(
        hold: HoldEntry,
        today: LocalDate,
    ): HoldViewStatus =
        when {
            hold.fulfilled && hold.collectByDate != null && today.isAfter(hold.collectByDate) -> HoldViewStatus.PICKUP_WINDOW_ENDED
            hold.fulfilled -> HoldViewStatus.READY_FOR_PICKUP
            else -> HoldViewStatus.WAITING
        }
}
