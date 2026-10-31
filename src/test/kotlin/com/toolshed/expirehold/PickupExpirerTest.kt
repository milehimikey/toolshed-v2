package com.toolshed.expirehold

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ReactionOutcome
import com.toolshed.axon.ToolId
import com.toolshed.pickupstoexpire.PickupToExpireRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

/** The Pickup Expirer's own behaviour: act on each lapsed set-aside, and treat a refusal as a log line, not a stop. */
class PickupExpirerTest {
    private fun uuid(seed: String) = UUID.nameUUIDFromBytes(seed.toByteArray())

    private val collectBy = LocalDate.of(2026, 10, 17)
    private val h1Row = PickupToExpireRow(HoldId(uuid("h1")), ToolId(uuid("drill")), NeighborId(uuid("ben")), collectBy)
    private val h3Row = PickupToExpireRow(HoldId(uuid("h3")), ToolId(uuid("saw")), NeighborId(uuid("cara")), collectBy)

    @Test
    fun `INV-EXP-2 - given two lapsed set-asides on the list, when the expirer runs, then it issues Expire Hold for each`() {
        val sent = mutableListOf<ExpireHold>()
        PickupExpirer({ listOf(h1Row, h3Row) }, { sent.add(it).let { ReactionOutcome.Accepted } }).run()
        assertThat(sent).containsExactly(ExpireHold(h1Row.holdId), ExpireHold(h3Row.holdId))
    }

    @Test
    fun `INV-EXP-2 - given nothing has lapsed, when the expirer runs, then no command is issued`() {
        val sent = mutableListOf<ExpireHold>()
        PickupExpirer({ emptyList() }, { sent.add(it).let { ReactionOutcome.Accepted } }).run()
        assertThat(sent).isEmpty()
    }

    @Test
    fun `INV-EXP-3 - given the first item is refused hold-ended, when the expirer runs, then it moves on to the next item`() {
        val sent = mutableListOf<ExpireHold>()
        val send = { command: ExpireHold ->
            sent.add(command)
            if (command.holdId == h1Row.holdId) {
                ReactionOutcome.Refused("INV-EXP-3", "hold-ended")
            } else {
                ReactionOutcome.Accepted
            }
        }
        PickupExpirer({ listOf(h1Row, h3Row) }, send).run()
        assertThat(sent).hasSize(2)
    }

    @Test
    fun `INV-EXP-11 - given the list shows the same lapsed set-aside on two runs, when run twice, then it sends each time`() {
        val sent = mutableListOf<ExpireHold>()
        val expirer = PickupExpirer({ listOf(h1Row) }, { sent.add(it).let { ReactionOutcome.Accepted } })
        expirer.run()
        expirer.run()
        assertThat(sent).containsExactly(ExpireHold(h1Row.holdId), ExpireHold(h1Row.holdId))
    }
}
