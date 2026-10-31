package com.toolshed.pickupnoticestosend

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
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.sendpickupnotice.PickupNoticeSent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * One test per scenario in slices/pickup-notices-to-send.md, driving [PickupNoticesToSendProjection] and
 * [ListPickupsToNotifyQueryHandler] directly against a fresh [InMemoryPickupNoticesToSendRepository].
 */
class PickupNoticesToSendProjectionTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val collectBy = LocalDate.of(2026, 10, 17)

    private val t1 = ToolId(uuid("t1"))
    private val t2 = ToolId(uuid("t2"))
    private val ben = NeighborId(uuid("n2"))
    private val cleo = NeighborId(uuid("n3"))
    private val h1 = HoldId(uuid("h1"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private val repository = InMemoryPickupNoticesToSendRepository()
    private val projection = PickupNoticesToSendProjection(repository)
    private val query = ListPickupsToNotifyQueryHandler(repository)

    private fun apply(vararg events: Any) {
        events.forEach { event ->
            when (event) {
                is NeighborRegistered -> projection.on(event)
                is NeighborEmailChanged -> projection.on(event)
                is ToolAdded -> projection.on(event)
                is HoldFulfilled -> projection.on(event)
                is PickupNoticeSent -> projection.on(event)
                is HoldCancelled -> projection.on(event)
                is HoldExpired -> projection.on(event)
                is ToolCheckedOut -> projection.on(event)
                is ToolRetired -> projection.on(event)
                is NeighborAccountClosed -> projection.on(event)
                else -> error("unexpected event $event")
            }
        }
    }

    private fun list(): List<PickupNoticesToSendRow> = query.handle(ListPickupsToNotify)

    private val benRegistered = NeighborRegistered(ben, "Ben Ito", "ben@example.org", now)
    private val drillAdded = ToolAdded(t1, "Cordless drill", null, 7, now)
    private val fulfilled = HoldFulfilled(h1, t1, ben, collectBy, now)
    private val h1Row = PickupNoticesToSendRow(h1, t1, ben, collectBy, "Ben Ito", "ben@example.org", "Cordless drill")

    private fun onList() = apply(benRegistered, drillAdded, fulfilled)

    @Test
    fun `INV-PNS-1 INV-PNS-2 - given Ben and drill known, when H1 fulfilled, then H1 is listed with all email fields`() {
        onList()
        assertThat(list()).containsExactly(h1Row)
    }

    @Test
    fun `INV-PNS-3 - given H1 is on the list, when Pickup Notice Sent for H1, then H1 leaves and never comes back`() {
        onList()
        apply(PickupNoticeSent(h1, "ben@example.org", "Ben Ito", "Cordless drill", collectBy, now))
        assertThat(list()).isEmpty()
        apply(fulfilled)
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-PNS-4 - given H1 is on the list, when Hold Cancelled for H1, then H1 leaves unsent`() {
        onList()
        apply(HoldCancelled(h1, now))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-PNS-4 - given H1 is on the list, when Hold Expired for H1, then H1 leaves unsent`() {
        onList()
        apply(HoldExpired(h1, now))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-PNS-4 - given H1 is on the list, when Ben checks out T1, then H1 leaves unsent`() {
        onList()
        apply(ToolCheckedOut(LoanId(uuid("l1")), t1, ben, 7, collectBy, now))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-PNS-4 - given H1 is on the list, when Ben checks out the ladder T2, then H1 stays`() {
        onList()
        apply(ToolCheckedOut(LoanId(uuid("l2")), t2, ben, 7, collectBy, now))
        assertThat(list()).containsExactly(h1Row)
    }

    @Test
    fun `INV-PNS-4 - given H1 is on the list, when someone else checks out T1, then H1 stays`() {
        onList()
        apply(ToolCheckedOut(LoanId(uuid("l3")), t1, cleo, 7, collectBy, now))
        assertThat(list()).containsExactly(h1Row)
    }

    @Test
    fun `INV-PNS-4 - given H1 is on the list, when T1 is retired, then H1 leaves unsent`() {
        onList()
        apply(ToolRetired(t1, now))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-PNS-4 - given H1 is on the list, when Ben closes his account, then H1 leaves unsent`() {
        onList()
        apply(NeighborAccountClosed(ben, false, now))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-PNS-2 - given H1 is on the list, when Ben changes his email, then H1 stays with the new address`() {
        onList()
        apply(NeighborEmailChanged(ben, "ben.ito@example.net", now))
        assertThat(list()).containsExactly(h1Row.copy(holderEmail = "ben.ito@example.net"))
    }

    @Test
    fun `INV-PNS-1 - given an empty list, when Cleo registers and a ladder is added, then the list is still empty`() {
        apply(NeighborRegistered(cleo, "Cleo Ray", "cleo@example.org", now), ToolAdded(t2, "Ladder", null, 7, now))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-PNS-7 - given H1 is on the list once, when the same Hold Fulfilled is applied again, then H1 is still listed once`() {
        onList()
        apply(fulfilled)
        assertThat(list()).containsExactly(h1Row)
    }

    @Test
    fun `INV-PNS-7 - given H1 left the list, when its ending event is applied again, then the list is unchanged`() {
        onList()
        apply(HoldCancelled(h1, now), HoldCancelled(h1, now))
        assertThat(list()).isEmpty()
    }
}
