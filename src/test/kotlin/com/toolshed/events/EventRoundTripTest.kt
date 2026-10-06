package com.toolshed.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.extendloan.LoanExtended
import com.toolshed.flagneighbor.NeighborFlagged
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordoverduenoticefailure.OverdueNoticeFailed
import com.toolshed.recordpickupnoticefailure.PickupNoticeFailed
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.sendoverduenotice.OverdueNoticeSent
import com.toolshed.sendpickupnotice.PickupNoticeSent
import com.toolshed.suspendneighbor.NeighborSuspended
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

class EventRoundTripTest {
    private val mapper: ObjectMapper =
        jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val today = LocalDate.of(2026, 10, 6)
    private val tool = ToolId(UUID.randomUUID())
    private val neighbor = NeighborId(UUID.randomUUID())
    private val loan = LoanId(UUID.randomUUID())
    private val hold = HoldId(UUID.randomUUID())

    private fun roundTrip(event: Any) {
        val json = mapper.writeValueAsString(event)
        assertEquals(event, mapper.readValue(json, event::class.java), "round trip of ${event::class.simpleName}")
    }

    @Test
    fun `all 20 emitted events round-trip through JSON`() {
        val events =
            listOf(
                ToolAdded(tool, "Drill", null, 7, now),
                NeighborRegistered(neighbor, "Ana", "ana@example.com", now),
                ToolCheckedOut(loan, tool, neighbor, 3, today, now),
                LoanExtended(loan, today, today.plusDays(7), now),
                HoldPlaced(hold, tool, neighbor, now),
                HoldCancelled(hold, now),
                OverdueNoticeSent(loan, "ana@example.com", "Ana", "Drill", today, now),
                OverdueNoticeFailed(loan, "bounced", now),
                NeighborEmailChanged(neighbor, "new@example.com", now),
                ToolReturned(loan, tool, now, today, false),
                HoldFulfilled(hold, tool, neighbor, today.plusDays(3), now),
                PickupNoticeSent(hold, "ana@example.com", "Ana", "Drill", today, now),
                PickupNoticeFailed(hold, "refused", now),
                HoldExpired(hold, now),
                NeighborFlagged(neighbor, listOf(loan, LoanId(UUID.randomUUID()), LoanId(UUID.randomUUID())), now),
                NeighborSuspended(neighbor, now),
                NeighborReinstated(neighbor, now),
                ToolRetired(tool, now),
                ToolLost(loan, tool, now),
                NeighborAccountClosed(neighbor, true, now),
            )
        assertEquals(20, events.size)
        events.forEach { roundTrip(it) }
    }

    @Test
    fun `optional description stays null when absent`() {
        val event = ToolAdded(tool, "Drill", null, 7, now)
        assertEquals(null, mapper.readValue(mapper.writeValueAsString(event), ToolAdded::class.java).description)
    }
}
