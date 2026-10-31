package com.toolshed.undeliverednotices

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
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
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.recordoverduenoticefailure.OverdueNoticeFailed
import com.toolshed.recordpickupnoticefailure.PickupNoticeFailed
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.web.Caller
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * One test per scenario in slices/undelivered-notices.md, driving [UndeliveredNoticesProjection] and
 * [ListUndeliveredNoticesQueryHandler] directly against a fresh [InMemoryUndeliveredNoticesRepository].
 * Together these cover every INV-UDN id in the doc.
 */
class UndeliveredNoticesProjectionTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")

    private val drill = ToolId(uuid("t1"))
    private val trimmer = ToolId(uuid("t2"))
    private val ana = NeighborId(uuid("n1"))
    private val ben = NeighborId(uuid("n2"))
    private val cara = NeighborId(uuid("n3"))
    private val l1 = LoanId(uuid("l1"))
    private val l2 = LoanId(uuid("l2"))
    private val h1 = HoldId(uuid("h1"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private fun at(
        day: Int,
        hour: Int = 9,
    ): Instant = Instant.parse("2026-10-%02dT%02d:00:00Z".format(day, hour))

    private fun oct(day: Int): LocalDate = LocalDate.of(2026, 10, day)

    private class Harness {
        val repository = InMemoryUndeliveredNoticesRepository()
        val projection = UndeliveredNoticesProjection(repository)
        val queryHandler = ListUndeliveredNoticesQueryHandler(repository)

        fun apply(vararg events: Any) {
            events.forEach { event ->
                when (event) {
                    is ToolAdded -> projection.on(event)
                    is NeighborRegistered -> projection.on(event)
                    is NeighborEmailChanged -> projection.on(event)
                    is ToolCheckedOut -> projection.on(event)
                    is LoanExtended -> projection.on(event)
                    is ToolReturned -> projection.on(event)
                    is ToolLost -> projection.on(event)
                    is HoldFulfilled -> projection.on(event)
                    is HoldCancelled -> projection.on(event)
                    is HoldExpired -> projection.on(event)
                    is ToolRetired -> projection.on(event)
                    is NeighborAccountClosed -> projection.on(event)
                    is OverdueNoticeFailed -> projection.on(event)
                    is PickupNoticeFailed -> projection.on(event)
                    else -> error("unexpected event ${event::class.simpleName}")
                }
            }
        }

        fun list(): List<UndeliveredNoticeRow> = queryHandler.handle(ListUndeliveredNotices).entries
    }

    /** The shared background: Ana has the drill on L1 due 12 October; Ben's hold H1 on the trimmer is set aside until 16 October. */
    private fun background(): Harness {
        val h = Harness()
        h.apply(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            ToolAdded(trimmer, "Hedge trimmer", null, 7, now),
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            NeighborRegistered(ben, "Ben Ito", "ben@example.org", now),
            ToolCheckedOut(l1, drill, ana, 7, oct(12), now),
            HoldFulfilled(h1, trimmer, ben, oct(16), now),
        )
        return h
    }

    // --- INV-UDN-1, INV-UDN-2, INV-UDN-3 ---

    @Test
    fun `INV-UDN-1 INV-UDN-2 INV-UDN-3 given empty view, when Overdue Notice Failed for L1 bounced, then lists Ana's entry`() {
        val h = background()
        assertThat(h.list()).isEmpty()

        h.apply(OverdueNoticeFailed(l1, "bounced", at(13)))

        val row = h.list().single()
        assertThat(row.kind).isEqualTo(NoticeKind.OVERDUE_NOTICE)
        assertThat(row.loanId).isEqualTo(l1)
        assertThat(row.holdId).isNull()
        assertThat(row.neighborId).isEqualTo(ana)
        assertThat(row.neighborName).isEqualTo("Ana Ruiz")
        assertThat(row.neighborEmail).isEqualTo("ana@example.org")
        assertThat(row.toolId).isEqualTo(drill)
        assertThat(row.toolName).isEqualTo("Cordless drill")
        assertThat(row.dueDate).isEqualTo(oct(12))
        assertThat(row.collectByDate).isNull()
        assertThat(row.failedAt).isEqualTo(at(13))
        assertThat(row.reason).isEqualTo("bounced")
    }

    @Test
    fun `INV-UDN-3 given L1 extended to 19 October, when Overdue Notice Failed as refused, then the entry shows due 19 October`() {
        val h = background()
        h.apply(LoanExtended(l1, oct(12), oct(19), now), OverdueNoticeFailed(l1, "refused", at(20)))

        val row = h.list().single()
        assertThat(row.dueDate).isEqualTo(oct(19))
        assertThat(row.failedAt).isEqualTo(at(20))
        assertThat(row.reason).isEqualTo("refused")
    }

    @Test
    fun `INV-UDN-1 INV-UDN-2 INV-UDN-3a given Ana's entry, when Pickup Notice Failed for H1 refused, then Ben's entry added`() {
        val h = background()
        h.apply(OverdueNoticeFailed(l1, "bounced", at(13)))
        val anaBefore = h.list().single()

        h.apply(PickupNoticeFailed(h1, "refused", at(13, 10)))

        val rows = h.list()
        assertThat(rows).hasSize(2)
        assertThat(rows.first { it.neighborId == ana }).isEqualTo(anaBefore)
        val ben = rows.first { it.kind == NoticeKind.PICKUP_NOTICE }
        assertThat(ben.holdId).isEqualTo(h1)
        assertThat(ben.loanId).isNull()
        assertThat(ben.neighborName).isEqualTo("Ben Ito")
        assertThat(ben.neighborEmail).isEqualTo("ben@example.org")
        assertThat(ben.toolName).isEqualTo("Hedge trimmer")
        assertThat(ben.collectByDate).isEqualTo(oct(16))
        assertThat(ben.dueDate).isNull()
        assertThat(ben.reason).isEqualTo("refused")
    }

    @Test
    fun `INV-UDN-1 given a failure for a loan or hold not open, when applied, then no entry is created`() {
        val h = background()
        h.apply(ToolReturned(l1, drill, at(11), oct(11), false), HoldCancelled(h1, now))

        h.apply(OverdueNoticeFailed(l1, "bounced", at(13)), PickupNoticeFailed(h1, "bounced", at(13)))

        assertThat(h.list()).isEmpty()
    }

    // --- INV-UDN-8 ---

    @Test
    fun `INV-UDN-8 given Ana's entry listed once, when the same Overdue Notice Failed is applied again, then still listed once`() {
        val h = background()
        val failed = OverdueNoticeFailed(l1, "bounced", at(13))
        h.apply(failed)
        h.apply(failed)

        assertThat(h.list()).hasSize(1)
    }

    @Test
    fun `INV-UDN-8 given an extended loan with an entry, when events are applied again, then due date and entry unchanged`() {
        val h = background()
        val checkedOut = ToolCheckedOut(l1, drill, ana, 7, oct(12), now)
        val failed = OverdueNoticeFailed(l1, "bounced", at(13))
        h.apply(LoanExtended(l1, oct(12), oct(19), now), failed)
        h.apply(checkedOut, failed, LoanExtended(l1, oct(12), oct(19), now))

        val row = h.list().single()
        assertThat(row.dueDate).isEqualTo(oct(19))
    }

    @Test
    fun `INV-UDN-8 given a dropped entry, when its events are applied again, then it does not come back`() {
        val h = background()
        val checkedOut = ToolCheckedOut(l1, drill, ana, 7, oct(12), now)
        val failed = OverdueNoticeFailed(l1, "bounced", at(13))
        h.apply(failed, ToolReturned(l1, drill, at(14), oct(14), true))
        h.apply(checkedOut, failed)

        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-UDN-8 given Ben's pickup entry once, when its events are applied again, then still listed once`() {
        val h = background()
        val failed = PickupNoticeFailed(h1, "refused", at(13))
        h.apply(failed)
        h.apply(HoldFulfilled(h1, trimmer, ben, oct(16), now), failed)

        assertThat(h.list()).hasSize(1)
    }

    @Test
    fun `INV-UDN-8 given an email changed, when Neighbor Registered is applied again, then the changed email stays`() {
        val h = background()
        h.apply(NeighborEmailChanged(ana, "ana.ruiz@example.net", now))
        h.apply(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now), OverdueNoticeFailed(l1, "bounced", at(13)))

        assertThat(h.list().single().neighborEmail).isEqualTo("ana.ruiz@example.net")
    }

    // --- INV-UDN-4 ---

    @Test
    fun `INV-UDN-4 given Ana's overdue entry, when Tool Returned for L1, then Ana's entry drops off`() {
        val h = background()
        h.apply(OverdueNoticeFailed(l1, "bounced", at(13)), ToolReturned(l1, drill, at(14), oct(14), true))

        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-UDN-4 given Ana's overdue entry, when Tool Lost for L1, then Ana's entry drops off`() {
        val h = background()
        h.apply(OverdueNoticeFailed(l1, "bounced", at(13)), ToolLost(l1, drill, at(14)))

        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-UDN-4 given Ana's overdue entry, when the drill is retired while out, then the entry stays until L1 is returned`() {
        val h = background()
        h.apply(OverdueNoticeFailed(l1, "bounced", at(13)), ToolRetired(drill, at(14)))

        assertThat(h.list()).hasSize(1)

        h.apply(ToolReturned(l1, drill, at(15), oct(15), true))
        assertThat(h.list()).isEmpty()
    }

    // --- INV-UDN-5 ---

    @Test
    fun `INV-UDN-5 given Ben's pickup entry, when Tool Checked Out for the trimmer and Ben, then Ben's entry drops off`() {
        val h = background()
        h.apply(PickupNoticeFailed(h1, "refused", at(13)), ToolCheckedOut(l2, trimmer, ben, 7, oct(23), at(14)))

        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-UDN-5 given Ben's entry past collect-by, when Cara checks out the trimmer, then it stays until Hold Expired`() {
        val h = background()
        h.apply(PickupNoticeFailed(h1, "refused", at(13)), ToolCheckedOut(l2, trimmer, cara, 7, oct(24), at(18)))

        assertThat(h.list()).hasSize(1)

        h.apply(HoldExpired(h1, at(19)))
        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-UDN-5 given Ben's pickup entry, when Hold Cancelled for H1, then Ben's entry drops off`() {
        val h = background()
        h.apply(PickupNoticeFailed(h1, "refused", at(13)), HoldCancelled(h1, at(14)))

        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-UDN-5 given Ben's pickup entry, when Hold Expired for H1, then Ben's entry drops off`() {
        val h = background()
        h.apply(PickupNoticeFailed(h1, "refused", at(13)), HoldExpired(h1, at(17)))

        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-UDN-5 given Ben's pickup entry, when the trimmer is retired, then Ben's entry drops off`() {
        val h = background()
        h.apply(PickupNoticeFailed(h1, "refused", at(13)), ToolRetired(trimmer, at(14)))

        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-UDN-5 given Ben's pickup entry, when Ben's account is closed, then Ben's entry drops off`() {
        val h = background()
        h.apply(PickupNoticeFailed(h1, "refused", at(13)), NeighborAccountClosed(ben, false, at(14)))

        assertThat(h.list()).isEmpty()
    }

    // --- INV-UDN-3 (email) ---

    @Test
    fun `INV-UDN-3 given Ana's entry with the bounced address, when Neighbor Email Changed, then shows the new email`() {
        val h = background()
        h.apply(OverdueNoticeFailed(l1, "bounced", at(13)), NeighborEmailChanged(ana, "ana.ruiz@example.net", at(14)))

        val row = h.list().single()
        assertThat(row.neighborEmail).isEqualTo("ana.ruiz@example.net")
    }

    // --- INV-UDN-9 ---

    @Test
    fun `INV-UDN-9 given Ben's pickup, Ana's overdue and Ana's pickup entries, when listed, then Ana's by failure time then Ben's`() {
        val h = background()
        val h2 = HoldId(uuid("h2"))
        val t3 = ToolId(uuid("t3"))
        h.apply(
            ToolAdded(t3, "Ladder", null, 7, now),
            HoldFulfilled(h2, t3, ana, oct(16), now),
            PickupNoticeFailed(h1, "refused", at(13)),
            OverdueNoticeFailed(l1, "bounced", at(14)),
            PickupNoticeFailed(h2, "refused", at(12)),
        )

        val rows = h.list()
        assertThat(rows.map { it.holdId to it.loanId }).containsExactly(h2 to null, null to l1, h1 to null)
    }

    @Test
    fun `INV-UDN-9 given names differing in case, when listed, then ordering ignores case`() {
        val h = background()
        val zed = NeighborId(uuid("n9"))
        val l9 = LoanId(uuid("l9"))
        h.apply(
            NeighborRegistered(zed, "aaron lowe", "aaron@example.org", now),
            ToolCheckedOut(l9, drill, zed, 7, oct(12), now),
            OverdueNoticeFailed(l1, "bounced", at(13)),
            OverdueNoticeFailed(l9, "bounced", at(14)),
        )

        assertThat(h.list().map { it.neighborName }).containsExactly("aaron lowe", "Ana Ruiz")
    }

    @Test
    fun `given Ana's overdue entry and Ben's pickup entry, when listed, then both come back with all their fields`() {
        val h = background()
        h.apply(OverdueNoticeFailed(l1, "bounced", at(13)), PickupNoticeFailed(h1, "refused", at(13)))

        val rows = h.list()
        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.neighborName }).containsExactly("Ana Ruiz", "Ben Ito")
    }

    // --- INV-UDN-10 ---

    @Test
    fun `INV-UDN-10 given entries, when a neighbor without the shed-manager role asks, then refused not-shed-manager`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller(neighborId = ben, isShedManager = false)) { requireUndeliveredNoticesReader() }
            }
        assertThat(refused.reason).isEqualTo("not-shed-manager")
        assertThat(refused.message).isEqualTo("Only the shed manager can see this screen.")
    }

    @Test
    fun `INV-UDN-10 given entries, when a caller with no sign-in asks, then refused not-signed-in`() {
        val refused = assertThrows<CommandRejected> { with(Caller.ANONYMOUS) { requireUndeliveredNoticesReader() } }
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }

    @Test
    fun `INV-UDN-10 given the shed manager, when asking, then allowed`() {
        with(Caller(neighborId = null, isShedManager = true)) { requireUndeliveredNoticesReader() }
    }
}
