package com.toolshed.holdstofulfill

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
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * One test per scenario in slices/holds-to-fulfill.md, driving [HoldsToFulfillProjection] and
 * [ListToolsToFulfillQueryHandler] directly against a fresh [InMemoryHoldsToFulfillRepository]. Together these
 * cover every INV-HTF id in the doc.
 */
class HoldsToFulfillProjectionTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val today = LocalDate.of(2026, 10, 6)

    private val t1 = ToolId(uuid("t1"))
    private val ana = NeighborId(uuid("n1"))
    private val ben = NeighborId(uuid("n2"))
    private val cleo = NeighborId(uuid("n3"))
    private val l1 = LoanId(uuid("l1"))
    private val l2 = LoanId(uuid("l2"))
    private val h1 = HoldId(uuid("h1"))
    private val h2 = HoldId(uuid("h2"))
    private val h3 = HoldId(uuid("h3"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private val repository = InMemoryHoldsToFulfillRepository()
    private val harnessProjection = HoldsToFulfillProjection(repository)
    private val harnessQuery = ListToolsToFulfillQueryHandler(repository)

    private fun apply(vararg events: Any) {
        events.forEach { event ->
            when (event) {
                is HoldPlaced -> harnessProjection.on(event)
                is HoldFulfilled -> harnessProjection.on(event)
                is HoldCancelled -> harnessProjection.on(event)
                is HoldExpired -> harnessProjection.on(event)
                is ToolCheckedOut -> harnessProjection.on(event)
                is ToolReturned -> harnessProjection.on(event)
                is ToolRetired -> harnessProjection.on(event)
                is ToolLost -> harnessProjection.on(event)
                is NeighborAccountClosed -> harnessProjection.on(event)
                else -> error("unexpected event $event")
            }
        }
    }

    private fun list(): List<HoldsToFulfillRow> = harnessQuery.handle(ListToolsToFulfill)

    private fun placed(
        hold: HoldId,
        neighbor: NeighborId,
    ) = HoldPlaced(hold, t1, neighbor, now)

    private fun checkedOut(
        loan: LoanId,
        neighbor: NeighborId,
    ) = ToolCheckedOut(loan, t1, neighbor, 7, today.plusDays(7), now)

    private fun returned(loan: LoanId) = ToolReturned(loan, t1, now, today, false)

    private fun fulfilled(
        hold: HoldId,
        neighbor: NeighborId,
    ) = HoldFulfilled(hold, t1, neighbor, today.plusDays(3), now)

    private fun assertFront(
        hold: HoldId,
        neighbor: NeighborId,
    ) = assertThat(list()).containsExactly(HoldsToFulfillRow(t1, hold, neighbor))

    @Test
    fun `INV-HTF-1 INV-HTF-2 - given T1 on loan with H1 before H2 waiting, when returned, then T1 is listed with front hold H1`() {
        apply(checkedOut(l1, ana), placed(h1, ben), placed(h2, cleo), returned(l1))
        assertFront(h1, ben)
    }

    @Test
    fun `INV-HTF-1 - given Ana has T1 on loan with no holds, when returned, then T1 is not listed`() {
        apply(checkedOut(l1, ana), returned(l1))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-HTF-1 - given Ana has T1 on loan, when Ben's hold is placed, then T1 is not listed`() {
        apply(checkedOut(l1, ana), placed(h1, ben))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-HTF-2 - given T1 on loan, when H1 and H2 placed at the same instant then returned, then recorded order decides H1`() {
        apply(checkedOut(l1, ana), placed(h1, ben), placed(h2, cleo), returned(l1))
        assertFront(h1, ben)
    }

    @Test
    fun `INV-HTF-3 - given T1 listed with H1 and H2 behind, when H1 is fulfilled, then T1 leaves the list`() {
        apply(placed(h1, ben), placed(h2, cleo))
        assertFront(h1, ben)
        apply(fulfilled(h1, ben))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-HTF-4 - given T1 set aside for H1 with H2 waiting, when H1 expires, then T1 is back with front hold H2`() {
        apply(placed(h1, ben), placed(h2, cleo), fulfilled(h1, ben), HoldExpired(h1, now))
        assertFront(h2, cleo)
    }

    @Test
    fun `INV-HTF-4 - given T1 set aside for H1 with no other hold, when H1 expires, then T1 is not listed`() {
        apply(placed(h1, ben), fulfilled(h1, ben), HoldExpired(h1, now))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-HTF-1 - given set-aside past collect-by but not expired, when listed, then not listed until Hold Expired lands`() {
        apply(placed(h1, ben), placed(h2, cleo), fulfilled(h1, ben))
        assertThat(list()).isEmpty()
        apply(HoldExpired(h1, now))
        assertFront(h2, cleo)
    }

    @Test
    fun `INV-HTF-1 INV-HTF-4 - given lapsed set-aside and Cleo collects first, when H1 expires then loan returns, then front hold H3`() {
        val dan = NeighborId(uuid("n4"))
        apply(placed(h1, ben), placed(h2, cleo), placed(h3, dan), fulfilled(h1, ben), checkedOut(l2, cleo))
        apply(HoldExpired(h1, now))
        assertThat(list()).isEmpty()
        apply(returned(l2))
        assertFront(h3, dan)
    }

    @Test
    fun `INV-HTF-4 - given T1 set aside for H1 with H2 waiting, when H1 is cancelled, then T1 is back with front hold H2`() {
        apply(placed(h1, ben), placed(h2, cleo), fulfilled(h1, ben), HoldCancelled(h1, now))
        assertFront(h2, cleo)
    }

    @Test
    fun `INV-HTF-4 - given T1 set aside for Ben's H1 and Cleo's H2 waiting, when Ben closes his account, then front hold H2`() {
        apply(placed(h1, ben), placed(h2, cleo), fulfilled(h1, ben), NeighborAccountClosed(ben, false, now))
        assertFront(h2, cleo)
    }

    @Test
    fun `INV-HTF-6 - given T1 listed with front H1 and H2 behind, when H1 is cancelled, then T1 stays with front hold H2`() {
        apply(placed(h1, ben), placed(h2, cleo), HoldCancelled(h1, now))
        assertFront(h2, cleo)
    }

    @Test
    fun `INV-HTF-6 - given T1 listed with only H1, when H1 is cancelled, then T1 leaves the list`() {
        apply(placed(h1, ben), HoldCancelled(h1, now))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-HTF-5 - given T1 listed with front H1 (Ben) and H2 behind, when Ben checks T1 out, then T1 leaves until the return`() {
        apply(placed(h1, ben), placed(h2, cleo), checkedOut(l1, ben))
        assertThat(list()).isEmpty()
        apply(returned(l1))
        assertFront(h2, cleo)
    }

    @Test
    fun `INV-HTF-7 - given T1 listed with H1, when T1 is retired, then T1 leaves the list`() {
        apply(placed(h1, ben), ToolRetired(t1, now))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-HTF-7 - given T1 on loan with H1 waiting and T1 retired, when the loan is returned, then T1 is not listed`() {
        apply(checkedOut(l1, ana), placed(h1, ben), ToolRetired(t1, now), returned(l1))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-HTF-7 - given T1 on loan L1 with H1 waiting, when T1 is recorded lost, then T1 never joins the list`() {
        apply(checkedOut(l1, ana), placed(h1, ben), ToolLost(l1, t1, now))
        assertThat(list()).isEmpty()
        apply(returned(l1))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `INV-HTF-9 - given T1 listed once with front H1, when the same Tool Returned is applied again, then T1 is still listed once`() {
        apply(checkedOut(l1, ana), placed(h1, ben), returned(l1), returned(l1))
        assertFront(h1, ben)
    }

    @Test
    fun `INV-HTF-9 - given a repeated Hold Placed and a replay for an ended hold, when applied again, then the queue is unchanged`() {
        apply(placed(h1, ben), placed(h1, ben), placed(h2, cleo), HoldCancelled(h1, now), HoldCancelled(h1, now), placed(h1, ben))
        assertFront(h2, cleo)
    }
}
