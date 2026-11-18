package com.toolshed.overdueloanstonotify

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.returntool.ToolReturned
import com.toolshed.sendoverduenotice.OverdueNoticeSent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** One test per scenario in slices/overdue-loans-to-notify.md, driving the projection and query handler directly. */
class OverdueLoansToNotifyProjectionTest {
    private val zone = ZoneId.of("America/Denver")
    private val oct12 = LocalDate.of(2026, 10, 12)
    private val oct13 = LocalDate.of(2026, 10, 13)
    private val now = Instant.parse("2026-10-01T16:00:00Z")

    private val drill = ToolId(uuid("drill"))
    private val ana = NeighborId(uuid("ana"))
    private val ben = NeighborId(uuid("ben"))
    private val l1 = LoanId(uuid("l1"))
    private val l2 = LoanId(uuid("l2"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private val repository = InMemoryOverdueLoansToNotifyRepository()
    private val projection = OverdueLoansToNotifyProjection(repository)

    private fun listedAt(instant: Instant): List<OverdueLoanToNotifyRow> =
        ListOverdueLoansToNotifyQueryHandler(repository, Clock.fixed(instant, zone)).handle(ListOverdueLoansToNotify)

    private fun listedOn(day: LocalDate): List<OverdueLoanToNotifyRow> = listedAt(day.atStartOfDay(zone).toInstant())

    /** Reads far in the future so every open loan counts as overdue: what is on the list, regardless of date. */
    private fun onList(): List<OverdueLoanToNotifyRow> = listedOn(LocalDate.of(2030, 1, 1))

    private fun known() {
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@oldmail.example", now))
        projection.on(NeighborRegistered(ben, "Ben Cole", "ben@example.org", now))
        projection.on(ToolAdded(drill, "Cordless drill", null, 7, now))
    }

    private fun out(
        loan: LoanId = l1,
        neighbor: NeighborId = ana,
        due: LocalDate = oct12,
    ) = ToolCheckedOut(loan, drill, neighbor, 7, due, now)

    private fun row(
        email: String = "ana@oldmail.example",
        due: LocalDate = oct12,
    ) = OverdueLoanToNotifyRow(l1, drill, ana, due, "Cordless drill", "Ana Ruiz", email)

    @Test
    fun `INV-OLN-1 - given L1 due 12 October, when the list is read at 23_59 on 12 October, then L1 is not returned`() {
        known()
        projection.on(out())
        assertThat(listedAt(Instant.parse("2026-10-13T05:59:00Z"))).isEmpty()
    }

    @Test
    fun `INV-OLN-1 INV-OLN-6 - given L1 due 12 October, when read at 00_00 on 13 October, then L1 returned with details`() {
        known()
        projection.on(out())
        assertThat(listedAt(Instant.parse("2026-10-13T06:00:00Z"))).containsExactly(row())
    }

    @Test
    fun `INV-OLN-1 - given L1 due 1 November, when DST changes, then boundary is still midnight shed time`() {
        known()
        projection.on(out(due = LocalDate.of(2026, 11, 1)))
        assertThat(listedAt(Instant.parse("2026-11-02T06:59:59Z"))).isEmpty()
        assertThat(listedAt(Instant.parse("2026-11-02T07:00:00Z"))).hasSize(1)
    }

    @Test
    fun `INV-OLN-1 - given L1 due 12 October and L2 due 14 October, when read on 13 October, then only L1 is returned`() {
        known()
        projection.on(out())
        projection.on(out(l2, ben, LocalDate.of(2026, 10, 14)))
        assertThat(listedOn(oct13).map { it.loanId }).containsExactly(l1)
    }

    @Test
    fun `INV-OLN-2 - given L1 due 12 October, when extended to 19 October, then not returned on 13 October and returned from 20 October`() {
        known()
        projection.on(out())
        projection.on(LoanExtended(l1, oct12, LocalDate.of(2026, 10, 19), now))
        assertThat(listedOn(oct13)).isEmpty()
        assertThat(listedOn(LocalDate.of(2026, 10, 19))).isEmpty()
        assertThat(listedOn(LocalDate.of(2026, 10, 20))).containsExactly(row(due = LocalDate.of(2026, 10, 19)))
    }

    @Test
    fun `INV-OLN-3 INV-OLN-8 - given L1 on the list, when Overdue Notice Sent for L1, then L1 leaves and is never returned again`() {
        known()
        projection.on(out())
        projection.on(sent())
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-OLN-4 INV-OLN-8 - given L1 on the list, when Tool Returned for L1 before any notice, then L1 leaves the list`() {
        known()
        projection.on(out())
        projection.on(ToolReturned(l1, drill, now, oct13, true))
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-OLN-4 INV-OLN-8 - given L1 on the list, when Tool Lost for L1, then L1 leaves the list`() {
        known()
        projection.on(out())
        projection.on(ToolLost(l1, drill, now))
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-OLN-4 - given L1 due 12 October still out and the drill retired, when read on 13 October, then L1 is returned`() {
        known()
        projection.on(out())
        // Tool Retired is not an event this view listens to: it must change nothing, so none is applied.
        assertThat(listedOn(oct13)).containsExactly(row())
    }

    @Test
    fun `INV-OLN-6 - given L1 on the list, when Neighbor Email Changed for Ana, then L1 stays carrying the new email`() {
        known()
        projection.on(out())
        projection.on(NeighborEmailChanged(ana, "ana@example.org", now))
        assertThat(onList()).containsExactly(row(email = "ana@example.org"))
    }

    @Test
    fun `INV-OLN-6 - given Ana's email changed before L1 is overdue, when read at 00_00 on 13 October, then L1 carries the new email`() {
        known()
        projection.on(out())
        projection.on(NeighborEmailChanged(ana, "ana@example.org", now))
        assertThat(listedAt(Instant.parse("2026-10-13T06:00:00Z"))).containsExactly(row(email = "ana@example.org"))
    }

    @Test
    fun `INV-OLN-9 - given L1 on the list once, when the same Tool Checked Out is applied again, then L1 is still there once`() {
        known()
        projection.on(out())
        projection.on(out())
        assertThat(onList()).containsExactly(row())
    }

    @Test
    fun `INV-OLN-9 - given a registration replayed after an email change, then the changed email stays`() {
        known()
        projection.on(out())
        projection.on(NeighborEmailChanged(ana, "ana@example.org", now))
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@oldmail.example", now))
        assertThat(onList()).containsExactly(row(email = "ana@example.org"))
    }

    @Test
    fun `INV-OLN-3 INV-OLN-9 - given notice sent for L1, when events are replayed, then L1 stays off the list`() {
        known()
        projection.on(out())
        projection.on(sent())
        projection.on(sent())
        projection.on(LoanExtended(l1, oct12, LocalDate.of(2026, 10, 19), now))
        projection.on(ToolReturned(l1, drill, now, oct13, true))
        projection.on(ToolLost(l1, drill, now))
        projection.on(out())
        assertThat(onList()).isEmpty()
    }

    private fun sent() = OverdueNoticeSent(l1, "ana@oldmail.example", "Ana Ruiz", "Cordless drill", oct12, now)
}
