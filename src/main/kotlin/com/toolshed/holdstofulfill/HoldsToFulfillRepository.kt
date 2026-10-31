package com.toolshed.holdstofulfill

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Holds To Fulfill's own storage (Amendment 5): in-memory, rebuilt from the event stream on restart. Every
 * mutation leaves the store as it was after the first application when repeated (INV-HTF-9).
 */
interface HoldsToFulfillRepository {
    fun place(
        holdId: HoldId,
        toolId: ToolId,
        neighborId: NeighborId,
    )

    fun fulfill(holdId: HoldId)

    fun end(holdId: HoldId)

    fun checkedOut(
        loanId: LoanId,
        toolId: ToolId,
        neighborId: NeighborId,
    )

    fun loanEnded(
        loanId: LoanId,
        toolId: ToolId,
    )

    fun retire(toolId: ToolId)

    fun endAllOnTool(toolId: ToolId)

    fun endAllForNeighbor(neighborId: NeighborId)

    /** Every tool that meets INV-HTF-1 right now, with its front hold (INV-HTF-2). */
    fun toolsToFulfill(): List<HoldsToFulfillRow>
}

private class WaitingHold(
    val holdId: HoldId,
    val neighborId: NeighborId,
    var setAside: Boolean = false,
)

private class ToolBook {
    val openLoans = mutableSetOf<LoanId>()
    var retired = false

    /** Holds that haven't ended, in the order Hold Placed was recorded (INV-HTF-2). */
    val holds = LinkedHashMap<HoldId, WaitingHold>()
}

class InMemoryHoldsToFulfillRepository : HoldsToFulfillRepository {
    private val tools = HashMap<ToolId, ToolBook>()
    private val holdTools = HashMap<HoldId, ToolId>()
    private val endedHolds = HashSet<HoldId>()
    private val endedLoans = HashSet<LoanId>()

    private fun book(toolId: ToolId): ToolBook = tools.getOrPut(toolId) { ToolBook() }

    @Synchronized
    override fun place(
        holdId: HoldId,
        toolId: ToolId,
        neighborId: NeighborId,
    ) {
        // INV-HTF-9: a repeat must not claim a second place in the queue, nor revive a hold that has ended.
        if (holdId in endedHolds || holdId in holdTools) return
        holdTools[holdId] = toolId
        book(toolId).holds[holdId] = WaitingHold(holdId, neighborId)
    }

    @Synchronized
    override fun fulfill(holdId: HoldId) {
        val toolId = holdTools[holdId] ?: return
        tools[toolId]?.holds?.get(holdId)?.setAside = true
    }

    @Synchronized
    override fun end(holdId: HoldId) {
        val toolId = holdTools.remove(holdId) ?: return
        tools[toolId]?.holds?.remove(holdId)
        endedHolds.add(holdId)
    }

    @Synchronized
    override fun checkedOut(
        loanId: LoanId,
        toolId: ToolId,
        neighborId: NeighborId,
    ) {
        // The checkout ends the holder's own hold on this tool (INV-HTF-5); other holders' holds are untouched.
        val book = book(toolId)
        book.holds.values
            .filter { it.neighborId == neighborId }
            .map { it.holdId }
            .forEach(::end)
        if (loanId !in endedLoans) book.openLoans.add(loanId)
    }

    @Synchronized
    override fun loanEnded(
        loanId: LoanId,
        toolId: ToolId,
    ) {
        endedLoans.add(loanId)
        book(toolId).openLoans.remove(loanId)
    }

    @Synchronized
    override fun retire(toolId: ToolId) {
        book(toolId).retired = true
        endAllOnTool(toolId)
    }

    @Synchronized
    override fun endAllOnTool(toolId: ToolId) {
        book(toolId)
            .holds.keys
            .toList()
            .forEach(::end)
    }

    @Synchronized
    override fun endAllForNeighbor(neighborId: NeighborId) {
        tools.values.forEach { book ->
            book.holds.values
                .filter { it.neighborId == neighborId }
                .map { it.holdId }
                .forEach(::end)
        }
    }

    @Synchronized
    override fun toolsToFulfill(): List<HoldsToFulfillRow> =
        tools.mapNotNull { (toolId, book) ->
            if (book.retired || book.openLoans.isNotEmpty() || book.holds.values.any { it.setAside }) return@mapNotNull null
            val front = book.holds.values.firstOrNull() ?: return@mapNotNull null
            HoldsToFulfillRow(toolId, front.holdId, front.neighborId)
        }
}

@Configuration
class HoldsToFulfillRepositoryConfiguration {
    @Bean
    fun holdsToFulfillRepository(): HoldsToFulfillRepository = InMemoryHoldsToFulfillRepository()
}
