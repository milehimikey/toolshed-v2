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
    private val now = Instant.parse("2026-10-01T16:00:00Z")

    private val drill = ToolId(uuid("drill"))
    private val saw = ToolId(uuid("saw"))
    private val ana = NeighborId(uuid("ana"))
    private val ben = NeighborId(uuid("ben"))
    private val l1 = LoanId(uuid("l1"))
    private val l2 = LoanId(uuid("l2"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private val repository = InMemoryOverdueLoansToNotifyRepository()
    private val projection = OverdueLoansToNotifyProjection(repository)

    private fun listedAt(instant: Instant): List<LoanToNotifyRow> =
        ListLoansToNotifyQueryHandler(repository, Clock.fixed(instant, zone)).handle(ListLoansToNotify)

    private fun listedOn(day: LocalDate): List<LoanToNotifyRow> = listedAt(day.atStartOfDay(zone).toInstant())

    /** Reads far in the future so every open loan counts as overdue: what's on the list, regardless of date. */
    private fun onList(): List<LoanToNotifyRow> = listedOn(LocalDate.of(2030, 1, 1))

    private fun known() {
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@oldmail.example", now))
        projection.on(NeighborRegistered(ben, "Ben Cole", "ben@example.org", now))
        projection.on(ToolAdded(drill, "Cordless drill", null, 7, now))
        projection.on(ToolAdded(saw, "Hand saw", null, 7, now))
    }

    private fun checkedOut(
        loan: LoanId = l1,
        tool: ToolId = drill,
        neighbor: NeighborId = ana,
        due: LocalDate = oct12,
    ) = ToolCheckedOut(loan, tool, neighbor, 7, due, now)

    private fun noticeSent(loan: LoanId = l1) = OverdueNoticeSent(loan, "ana@oldmail.example", "Ana Ruiz", "Cordless drill", oct12, now)

    private fun l1Row(email: String = "ana@oldmail.example") = LoanToNotifyRow(l1, drill, ana, oct12, "Cordless drill", "Ana Ruiz", email)

    @Test
    fun `INV-OLN-1 - given L1 due 12 October, when read at 23-59 on 12 October shed time, then L1 is not returned`() {
        known()
        projection.on(checkedOut())
        // 23:59 on 12 October in Denver (MDT, UTC-6) is 05:59Z on the 13th.
        assertThat(listedAt(Instant.parse("2026-10-13T05:59:00Z"))).isEmpty()
    }

    @Test
    fun `INV-OLN-1 INV-OLN-6 - given L1 due 12 October still out, when read at 00-00 on 13 October shed time, then L1 is returned with names and email`() {
        known()
        projection.on(checkedOut())
        assertThat(listedAt(Instant.parse("2026-10-13T06:00:00Z"))).containsExactly(l1Row())
    }

    @Test
    fun `INV-OLN-2 - given L1 due 12 October, when extended to 19 October, then not returned on 13 October and returned from 20 October`() {
        known()
        projection.on(checkedOut())
        projection.on(LoanExtended(l1, oct12, LocalDate.of(2026, 10, 19), now))
        assertThat(listedOn(LocalDate.of(2026, 10, 13))).isEmpty()
        assertThat(listedOn(LocalDate.of(2026, 10, 19))).isEmpty()
        assertThat(listedOn(LocalDate.of(2026, 10, 20)).map { it.dueDate }).containsExactly(LocalDate.of(2026, 10, 19))
    }

    @Test
    fun `INV-OLN-3 INV-OLN-8 - given L1 on the list, when Overdue Notice Sent, then L1 leaves and is never returned again`() {
        known()
        projection.on(checkedOut())
        assertThat(onList()).hasSize(1)
        projection.on(noticeSent())
        assertThat(onList()).isEmpty()
        projection.on(checkedOut())
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-OLN-4 - given L1 on the list, when Tool Returned before any notice, then L1 leaves`() {
        known()
        projection.on(checkedOut())
        projection.on(ToolReturned(l1, drill, now, oct12, false))
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-OLN-4 INV-OLN-8 - given L1 on the list, when Tool Lost, then L1 leaves`() {
        known()
        projection.on(checkedOut())
        projection.on(ToolLost(l1, drill, now))
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-OLN-4 - given L1 out and its tool retired, when read on 13 October, then L1 is still returned`() {
        // Tool Retired is not an event this view listens to: a retired tool's loan carries on.
        known()
        projection.on(checkedOut())
        assertThat(listedOn(LocalDate.of(2026, 10, 13))).containsExactly(l1Row())
    }

    @Test
    fun `INV-OLN-6 - given L1 on the list carrying the old email, when Neighbor Email Changed, then L1 carries the new one`() {
        known()
        projection.on(checkedOut())
        assertThat(onList()).containsExactly(l1Row())
        projection.on(NeighborEmailChanged(ana, "ana@example.org", now))
        assertThat(onList()).containsExactly(l1Row("ana@example.org"))
    }

    @Test
    fun `INV-OLN-6 - given email changed on 10 October before the loan is overdue, when read on 13 October, then L1 carries the new email`() {
        known()
        projection.on(checkedOut())
        projection.on(NeighborEmailChanged(ana, "ana@example.org", now))
        assertThat(listedOn(LocalDate.of(2026, 10, 13))).containsExactly(l1Row("ana@example.org"))
    }

    @Test
    fun `INV-OLN-1 - given L1 due 12 October and L2 due 14 October, when read on 13 October, then only L1 is returned`() {
        known()
        projection.on(checkedOut())
        projection.on(checkedOut(l2, saw, ben, LocalDate.of(2026, 10, 14)))
        assertThat(listedOn(LocalDate.of(2026, 10, 13)).map { it.loanId }).containsExactly(l1)
    }

    @Test
    fun `INV-OLN-9 - given L1 on the list once, when the same Tool Checked Out is applied again, then L1 is still on the list once`() {
        known()
        projection.on(checkedOut())
        projection.on(checkedOut())
        assertThat(onList()).containsExactly(l1Row())
    }

    @Test
    fun `INV-OLN-9 - given a registration replayed after an email change, then the current email is kept`() {
        known()
        projection.on(checkedOut())
        projection.on(NeighborEmailChanged(ana, "ana@example.org", now))
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@oldmail.example", now))
        assertThat(onList()).containsExactly(l1Row("ana@example.org"))
    }

    @Test
    fun `INV-OLN-3 INV-OLN-9 - given notice sent for L1, when the same Overdue Notice Sent is applied again, then L1 is still off the list`() {
        known()
        projection.on(checkedOut())
        projection.on(noticeSent())
        projection.on(noticeSent())
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-OLN-8 - given L1 has left the list, when a replayed Loan Extended, Tool Returned or Tool Lost arrives, then nothing changes`() {
        known()
        projection.on(checkedOut())
        projection.on(noticeSent())
        projection.on(LoanExtended(l1, oct12, LocalDate.of(2026, 10, 19), now))
        projection.on(ToolReturned(l1, drill, now, oct12, false))
        projection.on(ToolLost(l1, drill, now))
        assertThat(onList()).isEmpty()
    }
}
