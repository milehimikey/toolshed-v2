package com.toolshed.flaggedneighbors

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.extendloan.LoanExtended
import com.toolshed.flagneighbor.NeighborFlagged
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import com.toolshed.web.Caller
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * One test per scenario in slices/flagged-neighbors.md, driving [FlaggedNeighborsProjection] and
 * [ListFlaggedNeighborsQueryHandler] directly against a fresh [InMemoryFlaggedNeighborsRepository].
 * Together these cover every INV-FLN id in the doc.
 */
class FlaggedNeighborsProjectionTest {
    private val now = Instant.parse("2026-08-01T04:00:00Z")

    private val ana = NeighborId(uuid("n1"))
    private val ben = NeighborId(uuid("n2"))
    private val ladder = ToolId(uuid("t1"))
    private val drill = ToolId(uuid("t2"))
    private val trimmer = ToolId(uuid("t3"))
    private val l1 = LoanId(uuid("l1"))
    private val l2 = LoanId(uuid("l2"))
    private val l3 = LoanId(uuid("l3"))
    private val l4 = LoanId(uuid("l4"))
    private val l5 = LoanId(uuid("l5"))
    private val l6 = LoanId(uuid("l6"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private fun on(
        month: Int,
        day: Int,
        year: Int = 2026,
    ): Instant = Instant.parse("%d-%02d-%02dT15:00:00Z".format(year, month, day))

    private fun date(
        month: Int,
        day: Int,
        year: Int = 2026,
    ): LocalDate = LocalDate.of(year, month, day)

    private class Harness {
        val repository = InMemoryFlaggedNeighborsRepository()
        val projection = FlaggedNeighborsProjection(repository)
        val queryHandler = ListFlaggedNeighborsQueryHandler(repository)

        fun apply(vararg events: Any) {
            events.forEach { event ->
                when (event) {
                    is ToolAdded -> projection.on(event)
                    is NeighborRegistered -> projection.on(event)
                    is NeighborEmailChanged -> projection.on(event)
                    is ToolCheckedOut -> projection.on(event)
                    is LoanExtended -> projection.on(event)
                    is ToolReturned -> projection.on(event)
                    is NeighborFlagged -> projection.on(event)
                    is NeighborSuspended -> projection.on(event)
                    is NeighborAccountClosed -> projection.on(event)
                    else -> error("unexpected event ${event::class.simpleName}")
                }
            }
        }

        fun list(): List<FlaggedNeighborRow> = queryHandler.handle(ListFlaggedNeighbors).entries
    }

    /** Ana registered; L1-L3 (ladder, drill, trimmer) returned late; due 31 Aug, 15 Sep, 1 Oct. */
    private fun background(extendL2: Boolean = false): Harness {
        val h = Harness()
        h.apply(
            ToolAdded(ladder, "Ladder", null, 7, now),
            ToolAdded(drill, "Cordless drill", null, 7, now),
            ToolAdded(trimmer, "Hedge trimmer", null, 7, now),
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            NeighborRegistered(ben, "Ben Ito", "ben@example.org", now),
            ToolCheckedOut(l1, ladder, ana, 7, date(8, 31), now),
            ToolCheckedOut(l2, drill, ana, 7, date(9, 15), now),
            ToolCheckedOut(l3, trimmer, ana, 7, date(10, 1), now),
        )
        if (extendL2) h.apply(LoanExtended(l2, date(9, 15), date(9, 18), on(9, 10)))
        h.apply(
            ToolReturned(l1, ladder, on(9, 1), date(9, 1), true),
            ToolReturned(l2, drill, on(9, 20), date(9, 20), true),
            ToolReturned(l3, trimmer, on(10, 5), date(10, 5), true),
        )
        return h
    }

    private fun laterReturns(h: Harness) {
        h.apply(
            ToolCheckedOut(l4, ladder, ana, 7, date(10, 20), now),
            ToolCheckedOut(l5, drill, ana, 7, date(10, 25), now),
            ToolCheckedOut(l6, trimmer, ana, 7, date(10, 30), now),
            ToolReturned(l4, ladder, on(11, 1), date(11, 1), true),
            ToolReturned(l5, drill, on(11, 5), date(11, 5), true),
            ToolReturned(l6, trimmer, on(11, 10), date(11, 10), true),
        )
    }

    private val firstFlag get() = NeighborFlagged(ana, listOf(l1, l2, l3), on(10, 5))

    // --- INV-FLN-1, INV-FLN-2, INV-FLN-5 ---

    @Test
    fun `INV-FLN-1 INV-FLN-2 INV-FLN-5 given empty view, when Ana flagged naming L1 L2 L3, then lists Ana with three late returns`() {
        val h = background()
        assertThat(h.list()).isEmpty()

        h.apply(firstFlag)

        val row = h.list().single()
        assertThat(row.neighborId).isEqualTo(ana)
        assertThat(row.name).isEqualTo("Ana Ruiz")
        assertThat(row.email).isEqualTo("ana@example.org")
        assertThat(row.flagCount).isEqualTo(1)
        assertThat(row.latestFlaggedAt).isEqualTo(on(10, 5))
        assertThat(row.lateReturns).containsExactly(
            LateReturnRow(l1, "Ladder", date(8, 31), date(9, 1), on(10, 5)),
            LateReturnRow(l2, "Cordless drill", date(9, 15), date(9, 20), on(10, 5)),
            LateReturnRow(l3, "Hedge trimmer", date(10, 1), date(10, 5), on(10, 5)),
        )
    }

    // --- INV-FLN-2 ---

    @Test
    fun `INV-FLN-2 given L2 extended to 18 September before its return, when Neighbor Flagged, then the drill shows due 18 September`() {
        val h = background(extendL2 = true)

        h.apply(firstFlag)

        val drillRow =
            h
                .list()
                .single()
                .lateReturns
                .single { it.loanId == l2 }
        assertThat(drillRow.dueDate).isEqualTo(date(9, 18))
        assertThat(drillRow.returnedOn).isEqualTo(date(9, 20))
    }

    @Test
    fun `INV-FLN-2 given Flagged Neighbors lists Ana, when Neighbor Email Changed, then the email is the new one and flags unchanged`() {
        val h = background()
        h.apply(firstFlag)

        h.apply(NeighborEmailChanged(ana, "ana.ruiz@example.net", on(10, 6)))

        val row = h.list().single()
        assertThat(row.name).isEqualTo("Ana Ruiz")
        assertThat(row.email).isEqualTo("ana.ruiz@example.net")
        assertThat(row.flagCount).isEqualTo(1)
        assertThat(row.lateReturns).hasSize(3)
    }

    // --- INV-FLN-3 ---

    @Test
    fun `INV-FLN-3 given Ana listed, when flagged again 15 November, then one entry flagged twice with six late returns`() {
        val h = background()
        h.apply(firstFlag)
        laterReturns(h)

        h.apply(NeighborFlagged(ana, listOf(l4, l5, l6), on(11, 15)))

        val row = h.list().single()
        assertThat(row.flagCount).isEqualTo(2)
        assertThat(row.latestFlaggedAt).isEqualTo(on(11, 15))
        assertThat(row.lateReturns.map { it.loanId }).containsExactly(l1, l2, l3, l4, l5, l6)
        assertThat(row.lateReturns.take(3).map { it.flaggedAt }).containsOnly(on(10, 5))
        assertThat(row.lateReturns.drop(3).map { it.flaggedAt }).containsOnly(on(11, 15))
    }

    @Test
    fun `INV-FLN-3 given Ana suspended and reinstated, when flagged again 20 January, then a new entry with only new late returns`() {
        val h = background()
        h.apply(firstFlag)
        h.apply(NeighborSuspended(ana, on(10, 6)))
        laterReturns(h)

        h.apply(NeighborFlagged(ana, listOf(l4, l5, l6), on(1, 20, 2027)))

        val row = h.list().single()
        assertThat(row.flagCount).isEqualTo(1)
        assertThat(row.latestFlaggedAt).isEqualTo(on(1, 20, 2027))
        assertThat(row.lateReturns.map { it.loanId }).containsExactly(l4, l5, l6)
    }

    // --- INV-FLN-5 ---

    @Test
    fun `INV-FLN-5 given Ana and Ben listed, when Neighbor Suspended for Ana, then Ana leaves and Ben is still listed`() {
        val h = background()
        h.apply(firstFlag, NeighborFlagged(ben, listOf(l1), on(10, 6)))
        assertThat(h.list()).hasSize(2)

        h.apply(NeighborSuspended(ana, on(10, 7)))

        assertThat(h.list().map { it.neighborId }).containsExactly(ben)
    }

    // --- INV-FLN-6 ---

    @Test
    fun `INV-FLN-6 given Ana listed, when Neighbor Account Closed for Ana, then Ana leaves`() {
        val h = background()
        h.apply(firstFlag)

        h.apply(NeighborAccountClosed(ana, false, on(10, 7)))

        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-FLN-6 given an entry gone, when a later suspension or closure arrives, then nothing changes, new flag starts anew`() {
        val h = background()
        h.apply(firstFlag, NeighborSuspended(ana, on(10, 7)))

        h.apply(NeighborSuspended(ana, on(10, 8)), NeighborAccountClosed(ana, false, on(10, 9)))
        assertThat(h.list()).isEmpty()

        h.apply(NeighborFlagged(ana, listOf(l1), on(11, 1)))
        assertThat(h.list().single().flagCount).isEqualTo(1)
    }

    // --- INV-FLN-4 ---

    @Test
    fun `INV-FLN-4 given Ana listed since 5 October, when a month passes with no suspension for her, then Ana is still listed`() {
        val h = background()
        h.apply(firstFlag)

        h.apply(
            NeighborRegistered(ben, "Ben Ito", "ben@example.org", on(11, 1)),
            NeighborEmailChanged(ben, "ben2@example.org", on(11, 2)),
            NeighborSuspended(ben, on(11, 3)),
        )

        assertThat(h.list().map { it.neighborId }).containsExactly(ana)
    }

    // --- INV-FLN-9 ---

    @Test
    fun `INV-FLN-9 given Ana listed once, when the same Neighbor Flagged is applied again, then Ana is listed once flagged once`() {
        val h = background()
        h.apply(firstFlag)

        h.apply(firstFlag)

        val row = h.list().single()
        assertThat(row.flagCount).isEqualTo(1)
        assertThat(row.lateReturns).hasSize(3)
    }

    @Test
    fun `INV-FLN-9 given a replayed Tool Checked Out and Neighbor Registered, when applied again, then due date and email are not reset`() {
        val h = background(extendL2 = true)
        h.apply(NeighborEmailChanged(ana, "ana.ruiz@example.net", on(10, 1)))
        h.apply(
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            ToolCheckedOut(l2, drill, ana, 7, date(9, 15), now),
            ToolReturned(l2, drill, on(9, 25), date(9, 25), true),
        )

        h.apply(firstFlag)

        val row = h.list().single()
        assertThat(row.email).isEqualTo("ana.ruiz@example.net")
        val drillRow = row.lateReturns.single { it.loanId == l2 }
        assertThat(drillRow.dueDate).isEqualTo(date(9, 18))
        assertThat(drillRow.returnedOn).isEqualTo(date(9, 20))
    }

    // --- INV-FLN-10 ---

    @Test
    fun `INV-FLN-10 given Ben first flagged 1 October and Ana 5 October, when listed, then Ana Ruiz comes first then Ben Ito`() {
        val h = background()
        h.apply(NeighborFlagged(ben, listOf(l1), on(10, 1)), firstFlag)

        assertThat(h.list().map { it.name }).containsExactly("Ana Ruiz", "Ben Ito")
    }

    @Test
    fun `INV-FLN-10 given two neighbors with the same name ignoring case, when listed, then the earlier entry comes first`() {
        val h = background()
        val other = NeighborId(uuid("n9"))
        h.apply(NeighborRegistered(other, "ana ruiz", "other@example.org", now))
        h.apply(NeighborFlagged(other, listOf(l1), on(10, 1)), firstFlag)

        assertThat(h.list().map { it.neighborId }).containsExactly(other, ana)
    }

    // --- Listing flagged neighbors ---

    @Test
    fun `given Ana and Ben listed, when the screen lists flagged neighbors, then both come back with all fields`() {
        val h = background()
        h.apply(firstFlag, NeighborFlagged(ben, listOf(l1), on(10, 6)))

        val rows = h.list()

        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.email }).containsExactly("ana@example.org", "ben@example.org")
        assertThat(rows[0].lateReturns).hasSize(3)
        assertThat(rows[1].lateReturns).hasSize(1)
    }

    // --- INV-FLN-11 ---

    @Test
    fun `INV-FLN-11 given entries, when a neighbor without the shed-manager role asks, then refused not-shed-manager`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller(neighborId = ben, isShedManager = false)) { requireFlaggedNeighborsReader() }
            }
        assertThat(refused.reason).isEqualTo("not-shed-manager")
        assertThat(refused.message).isEqualTo("Only the shed manager can see this screen.")
    }

    @Test
    fun `INV-FLN-11 given entries, when a caller with no sign-in asks, then refused not-signed-in`() {
        val refused = assertThrows<CommandRejected> { with(Caller.ANONYMOUS) { requireFlaggedNeighborsReader() } }
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }

    @Test
    fun `INV-FLN-11 given the shed manager, when asking, then allowed`() {
        with(Caller(neighborId = null, isShedManager = true)) { requireFlaggedNeighborsReader() }
    }
}
