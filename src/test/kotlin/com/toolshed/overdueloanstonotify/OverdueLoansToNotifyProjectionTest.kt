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
    private val now = Instant.parse("2026-10-05T16:00:00Z")

    private val drill = ToolId(uuid("drill"))
    private val ana = NeighborId(uuid("ana"))
    private val ben = NeighborId(uuid("ben"))
    private val l1 = LoanId(uuid("l1"))
    private val l2 = LoanId(uuid("l2"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private val repository = InMemoryOverdueLoansToNotifyRepository()
    private val projection = OverdueLoansToNotifyProjection(repository)

    private fun listedAt(instant: Instant): List<OverdueLoanToNotifyRow> =
        OverdueLoansToNotifyQueryHandler(repository, Clock.fixed(instant, zone)).handle(ListOverdueLoansToNotify)

    private fun listedAt(
        day: LocalDate,
        hour: Int,
        minute: Int,
    ) = listedAt(day.atTime(hour, minute).atZone(zone).toInstant())

    /** Reads far in the future so every open loan counts as overdue: what's on the list, regardless of date. */
    private fun onList(): List<OverdueLoanToNotifyRow> = listedAt(LocalDate.of(2030, 1, 1), 0, 0)

    private fun setup() {
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@oldmail.example", now))
        projection.on(NeighborRegistered(ben, "Ben Cole", "ben@example.org", now))
        projection.on(ToolAdded(drill, "Cordless drill", null, 7, now))
    }

    private fun checkedOut(
        loan: LoanId = l1,
        neighbor: NeighborId = ana,
        due: LocalDate = oct12,
    ) = ToolCheckedOut(loan, drill, neighbor, 7, due, now)

    private fun l1Row(
        email: String = "ana@oldmail.example",
        due: LocalDate = oct12,
    ) = OverdueLoanToNotifyRow(l1, drill, ana, due, "Cordless drill", "Ana Ruiz", email)

    @Test
    fun `INV-OLN-1 - given L1 due 12 October, when the list is read at 23-59 on 12 October, then L1 is not returned`() {
        setup()
        projection.on(checkedOut())
        assertThat(listedAt(oct12, 23, 59)).isEmpty()
    }

    @Test
    fun `INV-OLN-1 INV-OLN-6 - given L1 due 12 October still out, when read at 00-00 on 13 October, then L1 is returned with its data`() {
        setup()
        projection.on(checkedOut())
        assertThat(listedAt(oct13, 0, 0)).containsExactly(l1Row())
    }

    @Test
    fun `INV-OLN-2 - given L1 due 12 October, when extended to 19 October on 10 October, then returned only from 20 October`() {
        setup()
        projection.on(checkedOut())
        projection.on(LoanExtended(l1, oct12, LocalDate.of(2026, 10, 19), now))
        assertThat(listedAt(oct13, 0, 0)).isEmpty()
        assertThat(listedAt(LocalDate.of(2026, 10, 19), 23, 59)).isEmpty()
        assertThat(listedAt(LocalDate.of(2026, 10, 20), 0, 0))
            .containsExactly(l1Row(due = LocalDate.of(2026, 10, 19)))
    }

    @Test
    fun `INV-OLN-3 INV-OLN-8 - given L1 on the list, when Overdue Notice Sent, then L1 leaves and is never returned again`() {
        setup()
        projection.on(checkedOut())
        assertThat(onList()).hasSize(1)
        projection.on(OverdueNoticeSent(l1, "ana@oldmail.example", "Ana Ruiz", "Cordless drill", oct12, now))
        assertThat(onList()).isEmpty()
        projection.on(LoanExtended(l1, oct12, LocalDate.of(2026, 10, 19), now))
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-OLN-4 INV-OLN-8 - given L1 on the list, when Tool Returned before any notice, then L1 leaves the list`() {
        setup()
        projection.on(checkedOut())
        projection.on(ToolReturned(l1, drill, now, oct13, true))
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-OLN-4 INV-OLN-8 - given L1 on the list, when Tool Lost, then L1 leaves the list`() {
        setup()
        projection.on(checkedOut())
        projection.on(ToolLost(l1, drill, now))
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-OLN-4 - given L1 due 12 October out and tool retired, when read 00-00 on 13 October, then L1 is returned`() {
        setup()
        projection.on(checkedOut())
        // Tool Retired is deliberately not handled: the loan carries on until return or loss.
        assertThat(listedAt(oct13, 0, 0)).containsExactly(l1Row())
    }

    @Test
    fun `INV-OLN-6 - given L1 on the list carrying the old email, when Neighbor Email Changed, then L1 carries the new one`() {
        setup()
        projection.on(checkedOut())
        projection.on(NeighborEmailChanged(ana, "ana@example.org", now))
        assertThat(onList()).containsExactly(l1Row(email = "ana@example.org"))
    }

    @Test
    fun `INV-OLN-6 - given email changed before L1 is overdue, when read at 00-00 on 13 October, then L1 carries the new one`() {
        setup()
        projection.on(checkedOut())
        projection.on(NeighborEmailChanged(ana, "ana@example.org", now))
        assertThat(listedAt(oct13, 0, 0)).containsExactly(l1Row(email = "ana@example.org"))
    }

    @Test
    fun `INV-OLN-1 - given L1 due 12 October and L2 due 14 October, when read on 13 October, then only L1 is returned`() {
        setup()
        projection.on(checkedOut())
        projection.on(checkedOut(l2, ben, LocalDate.of(2026, 10, 14)))
        assertThat(listedAt(oct13, 12, 0).map { it.loanId }).containsExactly(l1)
    }

    @Test
    fun `INV-OLN-9 - given L1 on the list once, when the same Tool Checked Out is applied again, then L1 is on the list once`() {
        setup()
        projection.on(checkedOut())
        projection.on(checkedOut())
        assertThat(onList()).containsExactly(l1Row())
    }

    @Test
    fun `INV-OLN-3 INV-OLN-9 - given Overdue Notice Sent applied for L1, when replayed, then L1 is still off the list`() {
        setup()
        val sent = OverdueNoticeSent(l1, "ana@oldmail.example", "Ana Ruiz", "Cordless drill", oct12, now)
        projection.on(checkedOut())
        projection.on(sent)
        projection.on(sent)
        projection.on(checkedOut())
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-OLN-9 - given every event applied twice, when read, then the list is as after once`() {
        val events =
            listOf(
                NeighborRegistered(ana, "Ana Ruiz", "ana@oldmail.example", now),
                ToolAdded(drill, "Cordless drill", null, 7, now),
                checkedOut(),
                LoanExtended(l1, oct12, oct13, now),
                NeighborEmailChanged(ana, "ana@example.org", now),
            )
        events.forEach { applyTwice(it) }
        assertThat(onList()).containsExactly(l1Row(email = "ana@example.org", due = oct13))
    }

    private fun applyTwice(event: Any) =
        repeat(2) {
            when (event) {
                is NeighborRegistered -> projection.on(event)
                is ToolAdded -> projection.on(event)
                is ToolCheckedOut -> projection.on(event)
                is LoanExtended -> projection.on(event)
                is NeighborEmailChanged -> projection.on(event)
                else -> error("unexpected $event")
            }
        }
}
