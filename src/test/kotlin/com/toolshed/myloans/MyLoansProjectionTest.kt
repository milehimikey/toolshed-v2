package com.toolshed.myloans

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The view's rules, applied to the in-memory read model: one row per open loan, holds, retirement, order, overdue. */
class MyLoansProjectionTest {
    private val now = Instant.parse("2026-10-05T10:00:00Z")
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val ladder = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d2"))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000a"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000b"))
    private val cara = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000c"))
    private val dan = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000d"))
    private val l1 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))
    private val l2 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000012"))
    private val dueOct12 = LocalDate.of(2026, 10, 12)

    // --- INV-MYL-2, INV-MYL-7, INV-MYL-12: tool checked out ---

    @Test
    fun `INV-MYL-2 INV-MYL-7 - given no loans, when Tool Checked Out, then My Loans lists it not overdue, notice none, extendable`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)

        val row = r.rows(ana, LocalDate.of(2026, 10, 5)).single()
        assertThat(row.toolName).isEqualTo("Cordless drill")
        assertThat(row.checkedOutAt).isEqualTo(now)
        assertThat(row.dueDate).isEqualTo(dueOct12)
        assertThat(row.overdue).isFalse()
        assertThat(row.overdueNotice).isEqualTo("none")
        assertThat(row.holdWaiting).isFalse()
        assertThat(row.toolRetired).isFalse()
        assertThat(row.canExtend).isTrue()
    }

    // --- INV-MYL-3: loan extended ---

    @Test
    fun `INV-MYL-3 - given L1 due 12 October, when Loan Extended to 19 October, then My Loans shows the new due date`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)

        r.extend(l1, LocalDate.of(2026, 10, 19))

        assertThat(r.rows(ana, dueOct12).single().dueDate).isEqualTo(LocalDate.of(2026, 10, 19))
    }

    // --- INV-MYL-4, INV-MYL-10: overdue is worked out against today, and gates extension ---

    @Test
    fun `INV-MYL-4 - given L1 due 12 October, when read at 23-59 on the 12th, then not overdue and extendable`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)

        val row = r.rows(ana, dueOct12).single()
        assertThat(row.overdue).isFalse()
        assertThat(row.canExtend).isTrue()
    }

    @Test
    fun `INV-MYL-4 INV-MYL-10 - given L1 due 12 October and not returned, when read on the 13th, then overdue and not extendable`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)

        val row = r.rows(ana, LocalDate.of(2026, 10, 13)).single()
        assertThat(row.overdue).isTrue()
        assertThat(row.canExtend).isFalse()
    }

    // --- INV-MYL-5: overdue notice ---

    @Test
    fun `INV-MYL-5 - given L1 overdue with notice none, when Overdue Notice Sent, then notice shows sent`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)

        r.noticeSent(l1)

        assertThat(r.rows(ana, dueOct12).single().overdueNotice).isEqualTo("sent")
    }

    @Test
    fun `INV-MYL-5 - given L1 with notice sent, when Overdue Notice Failed, then notice shows undelivered`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)
        r.noticeSent(l1)

        r.noticeFailed(l1)

        assertThat(r.rows(ana, dueOct12).single().overdueNotice).isEqualTo("undelivered")
    }

    // --- INV-MYL-6: returned or lost loans drop off ---

    @Test
    fun `INV-MYL-6 - given L1 the drill and L2 a ladder, when Tool Returned for L1, then L1 drops off and L2 is unchanged`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.addTool(ladder, "Ladder")
        r.checkOut(l1, drill, ana, now, dueOct12)
        r.checkOut(l2, ladder, ana, now, dueOct12)

        r.endLoan(l1)

        val rows = r.rows(ana, dueOct12)
        assertThat(rows.map { it.loanId }).containsExactly(l2)
    }

    @Test
    fun `INV-MYL-6 - given L2 overdue, when Tool Lost for L2, then L2 drops off`() {
        val r = MyLoansRepository()
        r.addTool(ladder, "Ladder")
        r.checkOut(l2, ladder, ana, now, dueOct12)

        r.recordLost(l2, ladder)

        assertThat(r.rows(ana, LocalDate.of(2026, 10, 13))).isEmpty()
    }

    // --- INV-MYL-9: holds gate extension ---

    @Test
    fun `INV-MYL-9 - given L1 with no hold waiting, when Hold Placed for Ben on the drill, then holdWaiting and not extendable`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)

        r.placeHold(HoldId(UUID.randomUUID()), drill, ben)

        val row = r.rows(ana, dueOct12).single()
        assertThat(row.holdWaiting).isTrue()
        assertThat(row.canExtend).isFalse()
    }

    @Test
    fun `INV-MYL-9 - given two holds waiting, when one is cancelled, then still holdWaiting because the other remains`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)
        val h1 = HoldId(UUID.randomUUID())
        val h2 = HoldId(UUID.randomUUID())
        r.placeHold(h1, drill, ben)
        r.placeHold(h2, drill, cara)

        r.endHold(h1)

        assertThat(r.rows(ana, dueOct12).single().holdWaiting).isTrue()
    }

    @Test
    fun `INV-MYL-9 - given only the last hold waiting, when it is cancelled, then no hold waiting and extendable again`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)
        val h2 = HoldId(UUID.randomUUID())
        r.placeHold(h2, drill, cara)

        r.endHold(h2)

        val row = r.rows(ana, dueOct12).single()
        assertThat(row.holdWaiting).isFalse()
        assertThat(row.canExtend).isTrue()
    }

    @Test
    fun `INV-MYL-9 - given a hold waiting from Ben only, when Neighbor Account Closed for Ben, then no hold waiting`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)
        r.placeHold(HoldId(UUID.randomUUID()), drill, ben)

        r.closeAccount(ben)

        assertThat(r.rows(ana, dueOct12).single().holdWaiting).isFalse()
    }

    // --- INV-MYL-9, INV-MYL-10, INV-MYL-12: tool retired while on loan ---

    @Test
    fun `INV-MYL-9 INV-MYL-10 - given a hold waiting on L1, when Tool Retired, then listed, retired, no hold waiting, not extendable`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)
        r.placeHold(HoldId(UUID.randomUUID()), drill, ben)

        r.retire(drill)

        val row = r.rows(ana, dueOct12).single()
        assertThat(row.dueDate).isEqualTo(dueOct12)
        assertThat(row.toolRetired).isTrue()
        assertThat(row.holdWaiting).isFalse()
        assertThat(row.canExtend).isFalse()
    }

    // --- INV-MYL-9: a loan can start with a hold already waiting, including one that ends with the very checkout ---

    @Test
    fun `INV-MYL-9 - given H1 for Ben and H2 for Cara set aside for Ben, when Ben checks the drill out, then H1 ends, H2 still waits`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.placeHold(HoldId(UUID.randomUUID()), drill, ben)
        r.placeHold(HoldId(UUID.randomUUID()), drill, cara)
        val l3 = LoanId(UUID.randomUUID())

        r.checkOut(l3, drill, ben, now, dueOct12)

        val row = r.rows(ben, dueOct12).single()
        assertThat(row.holdWaiting).isTrue()
        assertThat(row.canExtend).isFalse()
    }

    @Test
    fun `INV-MYL-9 - given Ben's hold expired, none other waiting, when Dan checks the drill out, then no hold waiting, extendable`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        val h1 = HoldId(UUID.randomUUID())
        r.placeHold(h1, drill, ben)
        r.endHold(h1)
        val l4 = LoanId(UUID.randomUUID())

        r.checkOut(l4, drill, dan, now, dueOct12)

        val row = r.rows(dan, dueOct12).single()
        assertThat(row.holdWaiting).isFalse()
        assertThat(row.canExtend).isTrue()
    }

    // --- INV-MYL-1: a neighbor's My Loans shows only that neighbor's own loans ---

    @Test
    fun `INV-MYL-1 - given Ana's L1, when Ben checks out a ladder as L5, then Ben's My Loans lists L5 and Ana's is unchanged`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.addTool(ladder, "Ladder")
        r.checkOut(l1, drill, ana, now, dueOct12)
        val before = r.rows(ana, dueOct12)
        val l5 = LoanId(UUID.randomUUID())

        r.checkOut(l5, ladder, ben, now, dueOct12)

        assertThat(r.rows(ana, dueOct12)).isEqualTo(before)
        assertThat(r.rows(ben, dueOct12).map { it.loanId }).containsExactly(l5)
        assertThat(r.rows(ana, dueOct12).map { it.loanId }).doesNotContain(l5)
    }

    @Test
    fun `INV-MYL-1 - given Ana has no open loans, when her loans are listed, then the list is empty`() {
        val r = MyLoansRepository()
        assertThat(r.rows(ana, dueOct12)).isEmpty()
    }

    // --- INV-MYL-8: unrelated events don't change the row (Neighbor Account Closed of a non-holder, a hold on another tool) ---

    @Test
    fun `INV-MYL-8 - given L1 the drill, when a hold is placed on a different tool, then L1 is unchanged`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.addTool(ladder, "Ladder")
        r.checkOut(l1, drill, ana, now, dueOct12)
        val before = r.rows(ana, dueOct12)

        r.placeHold(HoldId(UUID.randomUUID()), ladder, ben)

        assertThat(r.rows(ana, dueOct12)).isEqualTo(before)
    }

    // --- INV-MYL-11: applying the same event twice leaves the view as if applied once ---

    @Test
    fun `INV-MYL-11 - given L1 listed once, when the same Tool Checked Out is applied again, then it still lists once, unchanged`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)
        val before = r.rows(ana, dueOct12)

        r.checkOut(l1, drill, ana, now, dueOct12)

        assertThat(r.rows(ana, dueOct12)).isEqualTo(before)
    }

    @Test
    fun `INV-MYL-11 - given a hold ended, when Hold Cancelled is applied again, then nothing changes`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)
        val h1 = HoldId(UUID.randomUUID())
        r.placeHold(h1, drill, ben)
        r.endHold(h1)

        r.endHold(h1)

        assertThat(r.rows(ana, dueOct12).single().holdWaiting).isFalse()
    }

    // --- INV-MYL-13: list order ---

    @Test
    fun `INV-MYL-13 - given a ladder checked out on 3 October then a drill on 5 October, when listed, then the drill comes first`() {
        val r = MyLoansRepository()
        r.addTool(ladder, "Ladder")
        r.addTool(drill, "Cordless drill")
        val checkedOut3rd = Instant.parse("2026-10-03T09:00:00Z")
        val checkedOut5th = Instant.parse("2026-10-05T09:00:00Z")
        r.checkOut(l1, ladder, ana, checkedOut3rd, dueOct12)
        r.checkOut(l2, drill, ana, checkedOut5th, dueOct12)

        assertThat(r.rows(ana, dueOct12).map { it.loanId }).containsExactly(l2, l1)
    }

    @Test
    fun `INV-MYL-13 - given two loans checked out at the same instant, when listed, then they keep recording order`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        val first = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000021"))
        val second = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000022"))
        r.checkOut(first, drill, ana, now, dueOct12)
        r.checkOut(second, drill, ana, now, dueOct12)

        assertThat(r.rows(ana, dueOct12).map { it.loanId }).containsExactly(first, second)
    }

    // --- Events for a loan that has ended change nothing (post-return replay or late-arriving failure) ---

    @Test
    fun `given L1 already returned, when Overdue Notice Failed arrives for it, then nothing reappears`() {
        val r = MyLoansRepository()
        r.addTool(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, now, dueOct12)
        r.endLoan(l1)

        r.noticeFailed(l1)

        assertThat(r.rows(ana, dueOct12)).isEmpty()
    }
}
