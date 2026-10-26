package com.toolshed.myholds

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordpickupnoticefailure.PickupNoticeFailed
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.sendpickupnotice.PickupNoticeSent
import com.toolshed.web.Caller
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * One test per scenario in slices/my-holds.md, driving [MyHoldsProjection] and [ListMyHoldsQueryHandler] directly
 * against a fresh [InMemoryMyHoldsRepository] — the fastest-feedback unit level for a projection
 * (axon-projection-testing). Together these scenarios cover every INV-MYHD id in the doc.
 */
class MyHoldsProjectionTest {
    private val shedZone = ZoneId.of("America/Denver")
    private val now = Instant.parse("2026-10-06T04:00:00Z")

    private val drill = ToolId(uuid("t1"))
    private val ladder = ToolId(uuid("t5"))
    private val ana = NeighborId(uuid("n1"))
    private val ben = NeighborId(uuid("n2"))
    private val cara = NeighborId(uuid("n3"))
    private val dan = NeighborId(uuid("n4"))
    private val l1 = LoanId(uuid("l1"))
    private val h1 = HoldId(uuid("h1"))
    private val h2 = HoldId(uuid("h2"))
    private val h3 = HoldId(uuid("h3"))
    private val h4 = HoldId(uuid("h4"))
    private val h5 = HoldId(uuid("h5"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private fun clockAt(
        date: LocalDate,
        hour: Int,
        minute: Int,
    ): Clock = Clock.fixed(ZonedDateTime.of(date, java.time.LocalTime.of(hour, minute), shedZone).toInstant(), shedZone)

    private class Harness(
        clock: Clock,
    ) {
        val repository = InMemoryMyHoldsRepository()
        val projection = MyHoldsProjection(repository)
        val queryHandler = ListMyHoldsQueryHandler(repository, clock)

        fun apply(vararg events: Any) {
            events.forEach { event ->
                when (event) {
                    is ToolAdded -> projection.on(event)
                    is HoldPlaced -> projection.on(event)
                    is HoldFulfilled -> projection.on(event)
                    is PickupNoticeSent -> projection.on(event)
                    is PickupNoticeFailed -> projection.on(event)
                    is HoldCancelled -> projection.on(event)
                    is HoldExpired -> projection.on(event)
                    is ToolCheckedOut -> projection.on(event)
                    is ToolRetired -> projection.on(event)
                    is ToolLost -> projection.on(event)
                    is NeighborAccountClosed -> projection.on(event)
                    else -> error("unexpected event ${event::class.simpleName}")
                }
            }
        }

        fun listFor(neighborId: NeighborId): List<MyHoldsRow> = queryHandler.handle(ListMyHolds(neighborId))
    }

    private fun harness(clock: Clock = Clock.fixed(now, shedZone)) = Harness(clock)

    @Test
    fun `INV-MYHD-2 INV-MYHD-14 INV-MYHD-16 given no holds on the drill, when Ben's hold H1 is placed, then listed waiting position 1`() {
        val h = harness()
        h.apply(ToolAdded(drill, "Cordless drill", null, 7, now), HoldPlaced(h1, drill, ben, now))

        val rows = h.listFor(ben)
        assertThat(rows).hasSize(1)
        val row = rows.single()
        assertThat(row.toolName).isEqualTo("Cordless drill")
        assertThat(row.status).isEqualTo(HoldViewStatus.WAITING)
        assertThat(row.position).isEqualTo(1)
        assertThat(row.pickupNotice).isEqualTo(PickupNotice.NONE)
        assertThat(row.canCancel).isTrue()
    }

    @Test
    fun `INV-MYHD-2 given Ben's H1 at position 1, when Cara's H2 is placed, then Cara is position 2 and Ben unchanged`() {
        val h = harness()
        h.apply(ToolAdded(drill, "Cordless drill", null, 7, now), HoldPlaced(h1, drill, ben, now), HoldPlaced(h2, drill, cara, now))

        assertThat(h.listFor(cara).single().position).isEqualTo(2)
        assertThat(h.listFor(ben).single().position).isEqualTo(1)
    }

    @Test
    fun `INV-MYHD-2 given Ben's H1 waiting, when Cara's H2 then Dan's H3 are placed with the same placedAt, then order recorded decides`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldPlaced(h2, drill, cara, now),
            HoldPlaced(h3, drill, dan, now),
        )

        assertThat(h.listFor(cara).single().position).isEqualTo(2)
        assertThat(h.listFor(dan).single().position).isEqualTo(3)
    }

    @Test
    fun `INV-MYHD-6 INV-MYHD-2 given Ben H1 and Cara H2 waiting, when H1 is cancelled, then H1 drops and Cara is position 1`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldPlaced(h2, drill, cara, now),
            HoldCancelled(h1, now),
        )

        assertThat(h.listFor(ben)).isEmpty()
        assertThat(h.listFor(cara).single().position).isEqualTo(1)
    }

    @Test
    fun `INV-MYHD-3 INV-MYHD-2 given Ben H1 and Cara H2 waiting on T1, when H1 is fulfilled, then Ben ready-for-pickup Cara position 1`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldPlaced(h2, drill, cara, now),
            HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 17), now),
        )

        val benRow = h.listFor(ben).single()
        assertThat(benRow.status).isEqualTo(HoldViewStatus.READY_FOR_PICKUP)
        assertThat(benRow.collectByDate).isEqualTo(LocalDate.of(2026, 10, 17))
        assertThat(benRow.position).isNull()
        assertThat(h.listFor(cara).single().position).isEqualTo(1)
    }

    @Test
    fun `INV-MYHD-13 given Ben H1 ready-for-pickup with notice none, when Pickup Notice Sent is recorded, then notice sent`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 17), now),
            PickupNoticeSent(h1, "ben@example.org", "Ben Cole", "Cordless drill", LocalDate.of(2026, 10, 17), now),
        )

        assertThat(h.listFor(ben).single().pickupNotice).isEqualTo(PickupNotice.SENT)
    }

    @Test
    fun `INV-MYHD-13 given Ben H1 ready notice sent, when Pickup Notice Failed, then undelivered still ready-for-pickup`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 17), now),
            PickupNoticeSent(h1, "ben@example.org", "Ben Cole", "Cordless drill", LocalDate.of(2026, 10, 17), now),
            PickupNoticeFailed(h1, "bounced", now),
        )

        val row = h.listFor(ben).single()
        assertThat(row.pickupNotice).isEqualTo(PickupNotice.UNDELIVERED)
        assertThat(row.status).isEqualTo(HoldViewStatus.READY_FOR_PICKUP)
        assertThat(row.collectByDate).isEqualTo(LocalDate.of(2026, 10, 17))
    }

    @Test
    fun `INV-MYHD-17 given Ben H1 ready collect by 17 Oct, when listed 23 59 on 17 Oct shed time, then still ready-for-pickup`() {
        val h = harness(clockAt(LocalDate.of(2026, 10, 17), 23, 59))
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 17), now),
        )

        assertThat(h.listFor(ben).single().status).isEqualTo(HoldViewStatus.READY_FOR_PICKUP)
    }

    @Test
    fun `INV-MYHD-17 INV-MYHD-16 given Ben H1 ready not expired, when listed 00 00 18 Oct shed time, then window-ended cancel offered`() {
        val h = harness(clockAt(LocalDate.of(2026, 10, 18), 0, 0))
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 17), now),
        )

        val row = h.listFor(ben).single()
        assertThat(row.status).isEqualTo(HoldViewStatus.PICKUP_WINDOW_ENDED)
        assertThat(row.canCancel).isTrue()
    }

    @Test
    fun `INV-MYHD-4 given Ben H1 pickup-window-ended and Cara H2 waiting, when Hold Expired for H1, then H1 drops Cara stays waiting`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 17), now),
            HoldPlaced(h2, drill, cara, now),
            HoldExpired(h1, now),
        )

        assertThat(h.listFor(ben)).isEmpty()
        val caraRow = h.listFor(cara).single()
        assertThat(caraRow.status).isEqualTo(HoldViewStatus.WAITING)
        assertThat(caraRow.position).isEqualTo(1)
    }

    @Test
    fun `INV-MYHD-3 INV-MYHD-11 given Ben is suspended and H1 waiting, when T1 returned and Hold Fulfilled, then Ben ready-for-pickup`() {
        // My Holds hears nothing about suspension (INV-MYHD-11): no suspension event is even in its allowed set.
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 17), now),
        )

        val row = h.listFor(ben).single()
        assertThat(row.status).isEqualTo(HoldViewStatus.READY_FOR_PICKUP)
        assertThat(row.collectByDate).isEqualTo(LocalDate.of(2026, 10, 17))
    }

    @Test
    fun `INV-MYHD-5 given Ben H1 ready and Cara H2 waiting, when Tool Checked Out for T1 and Ben, then H1 drops Cara unchanged`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 17), now),
            HoldPlaced(h2, drill, cara, now),
            ToolCheckedOut(l1, drill, ben, 7, LocalDate.of(2026, 10, 24), now),
        )

        assertThat(h.listFor(ben)).isEmpty()
        assertThat(h.listFor(cara).single().position).isEqualTo(1)
    }

    @Test
    fun `INV-MYHD-5 INV-MYHD-2 given Ben H1 and Cara H2 waiting on T1 just returned, when Ben checks out T1 first, then Cara position 1`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldPlaced(h2, drill, cara, now),
            ToolCheckedOut(l1, drill, ben, 7, LocalDate.of(2026, 10, 24), now),
        )

        assertThat(h.listFor(ben)).isEmpty()
        assertThat(h.listFor(cara).single().position).isEqualTo(1)
    }

    @Test
    fun `INV-MYHD-6 given Ben H1 ready-for-pickup and Cara H2 waiting, when Hold Cancelled for H1, then H1 drops and Cara stays waiting`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 17), now),
            HoldPlaced(h2, drill, cara, now),
            HoldCancelled(h1, now),
        )

        assertThat(h.listFor(ben)).isEmpty()
        val caraRow = h.listFor(cara).single()
        assertThat(caraRow.status).isEqualTo(HoldViewStatus.WAITING)
        assertThat(caraRow.position).isEqualTo(1)
    }

    @Test
    fun `INV-MYHD-7 given Ben H1 and Cara H2 on T1, when Tool Retired recorded for T1, then both drop`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldPlaced(h2, drill, cara, now),
            ToolRetired(drill, now),
        )

        assertThat(h.listFor(ben)).isEmpty()
        assertThat(h.listFor(cara)).isEmpty()
    }

    @Test
    fun `INV-MYHD-7 given Ana has T1 on loan L1 and Ben H1 and Cara H2 on T1, when Tool Lost recorded for L1, then both drop`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldPlaced(h2, drill, cara, now),
            ToolLost(l1, drill, now),
        )

        assertThat(h.listFor(ben)).isEmpty()
        assertThat(h.listFor(cara)).isEmpty()
        // The loan L1 passed to this view only through its toolId; proving it ignores ana's own absence of holds too.
        assertThat(h.listFor(ana)).isEmpty()
    }

    @Test
    fun `INV-MYHD-8 given Ben H1 and Cara H2 waiting on T1, when Neighbor Account Closed for Ben, then H1 drops and Cara position 1`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldPlaced(h2, drill, cara, now),
            NeighborAccountClosed(ben, false, now),
        )

        assertThat(h.listFor(ben)).isEmpty()
        assertThat(h.listFor(cara).single().position).isEqualTo(1)
    }

    @Test
    fun `INV-MYHD-2 given Ben H1 expired and T1 set aside for Cara H2, when Ben's new hold H4 is placed, then Ben waiting behind Cara`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 9), now),
            HoldExpired(h1, now),
            HoldPlaced(h2, drill, cara, now),
            HoldFulfilled(h2, drill, cara, LocalDate.of(2026, 10, 24), now),
            HoldPlaced(h4, drill, ben, now),
        )

        val benRow = h.listFor(ben).single()
        assertThat(benRow.status).isEqualTo(HoldViewStatus.WAITING)
        assertThat(benRow.position).isEqualTo(1)
    }

    @Test
    fun `INV-MYHD-11 given Ana has no holds, when the hammer is checked out to Ana, then nobody's My Holds changes`() {
        val h = harness()
        h.apply(
            ToolAdded(ladder, "Hammer", null, 7, now),
            ToolCheckedOut(l1, ladder, ana, 7, LocalDate.of(2026, 10, 24), now),
        )

        assertThat(h.listFor(ana)).isEmpty()
    }

    @Test
    fun `INV-MYHD-1 given Ben H1 and Cara H2 both on T1, when Ben's holds are listed, then only H1 is returned`() {
        val h = harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h1, drill, ben, now),
            HoldPlaced(h2, drill, cara, now),
        )

        val rows = h.listFor(ben)
        assertThat(rows).hasSize(1)
        assertThat(rows.single().holdId).isEqualTo(h1)
    }

    @Test
    fun `INV-MYHD-18 given Ben placed H5 on the ladder then H1 on the drill, when his holds are listed, then the drill comes first`() {
        val h = harness()
        h.apply(
            ToolAdded(ladder, "Ladder", null, 7, now),
            ToolAdded(drill, "Cordless drill", null, 7, now),
            HoldPlaced(h5, ladder, ben, now),
            HoldPlaced(h1, drill, ben, now),
        )

        val rows = h.listFor(ben)
        assertThat(rows.map { it.holdId }).containsExactly(h1, h5)
    }

    @Test
    fun `INV-MYHD-15 given Ben H1 at position 1, when the same Hold Placed is applied again, then listed once at position 1`() {
        val h = harness()
        h.apply(ToolAdded(drill, "Cordless drill", null, 7, now), HoldPlaced(h1, drill, ben, now))
        h.apply(HoldPlaced(h1, drill, ben, now))
        h.apply(HoldPlaced(h2, drill, cara, now))

        val rows = h.listFor(ben)
        assertThat(rows).hasSize(1)
        assertThat(rows.single().position).isEqualTo(1)
        assertThat(h.listFor(cara).single().position).isEqualTo(2)
    }

    @Test
    fun `INV-MYHD-19 given nobody is signed in, when My Holds is asked for, then refused not-signed-in`() {
        val refused = assertThrows<CommandRejected> { with(Caller.ANONYMOUS) { requireLister(ListMyHolds(ben)) } }
        assertThat(refused.invariant).isEqualTo("INV-MYHD-19")
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }

    @Test
    fun `INV-MYHD-20 given Ben N2 is signed in, when Ana N1's holds are asked for, then refused requester-mismatch`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller(neighborId = ben, isShedManager = false)) { requireLister(ListMyHolds(ana)) }
            }
        assertThat(refused.invariant).isEqualTo("INV-MYHD-20")
        assertThat(refused.reason).isEqualTo("requester-mismatch")
        assertThat(refused.message).isEqualTo("You can only see your own holds.")
    }
}
