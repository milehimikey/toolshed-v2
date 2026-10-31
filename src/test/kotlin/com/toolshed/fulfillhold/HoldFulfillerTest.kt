package com.toolshed.fulfillhold

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ReactionOutcome
import com.toolshed.axon.ToolId
import com.toolshed.holdstofulfill.HoldsToFulfillRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/** The Hold Fulfiller's own behaviour: act on each item, and treat a refusal as a log line, not a stop. */
class HoldFulfillerTest {
    private fun uuid(seed: String) = UUID.nameUUIDFromBytes(seed.toByteArray())

    private val drillRow = HoldsToFulfillRow(ToolId(uuid("drill")), HoldId(uuid("h1")), NeighborId(uuid("ben")))
    private val sawRow = HoldsToFulfillRow(ToolId(uuid("saw")), HoldId(uuid("h3")), NeighborId(uuid("cara")))

    @Test
    fun `INV-FUL-2 - given two tools on the list, when the fulfiller runs, then it issues Fulfill Hold for each front hold`() {
        val sent = mutableListOf<FulfillHold>()
        HoldFulfiller({ listOf(drillRow, sawRow) }, { sent.add(it).let { ReactionOutcome.Accepted } }).run()
        assertThat(sent).containsExactly(FulfillHold(drillRow.holdId), FulfillHold(sawRow.holdId))
    }

    @Test
    fun `given an empty list, when the fulfiller runs, then no command is issued`() {
        val sent = mutableListOf<FulfillHold>()
        HoldFulfiller({ emptyList() }, { sent.add(it).let { ReactionOutcome.Accepted } }).run()
        assertThat(sent).isEmpty()
    }

    @Test
    fun `INV-FUL-3 - given the first item is refused hold-ended, when the fulfiller runs, then it moves on to the next item`() {
        val sent = mutableListOf<FulfillHold>()
        val send = { command: FulfillHold ->
            sent.add(command)
            if (command.holdId == drillRow.holdId) {
                ReactionOutcome.Refused("INV-FUL-3", "hold-ended")
            } else {
                ReactionOutcome.Accepted
            }
        }
        HoldFulfiller({ listOf(drillRow, sawRow) }, send).run()
        assertThat(sent).hasSize(2)
    }

    @Test
    fun `INV-FUL-10 - given the list shows the same item twice, when the fulfiller runs twice, then it is sent again`() {
        val sent = mutableListOf<FulfillHold>()
        val fulfiller = HoldFulfiller({ listOf(drillRow) }, { sent.add(it).let { ReactionOutcome.Accepted } })
        fulfiller.run()
        fulfiller.run()
        assertThat(sent).containsExactly(FulfillHold(drillRow.holdId), FulfillHold(drillRow.holdId))
    }

    @Test
    fun `given the list has not caught up yet, when the fulfiller runs, then it re-reads and acts once the item appears`() {
        val sent = mutableListOf<FulfillHold>()
        val pauses = mutableListOf<Long>()
        var reads = 0
        val list = { if (++reads < 3) emptyList() else listOf(drillRow) }
        HoldFulfiller(list, { sent.add(it).let { ReactionOutcome.Accepted } }, listOf(10, 20, 30), { pauses.add(it) }).run()
        assertThat(sent).containsExactly(FulfillHold(drillRow.holdId))
        assertThat(pauses).containsExactly(10L, 20L)
    }

    @Test
    fun `given the list stays empty, when the fulfiller runs, then it gives up after the bounded waits`() {
        val pauses = mutableListOf<Long>()
        HoldFulfiller({ emptyList() }, { error("nothing to send") }, listOf(10, 20), { pauses.add(it) }).run()
        assertThat(pauses).containsExactly(10L, 20L)
    }
}
