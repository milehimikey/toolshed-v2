package com.toolshed.toolinventory

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.web.Caller
import com.toolshed.web.NOT_SHED_MANAGER
import com.toolshed.web.NOT_SIGNED_IN
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The view's rules, with the projection's handlers applied in sequence to the in-memory read model, then read. */
class ToolInventoryProjectionTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val today = LocalDate.of(2026, 10, 6)
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val drill2 = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d4"))
    private val ladder = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d2"))
    private val trimmer = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d3"))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"))
    private val l1 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))
    private val l2 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000012"))
    private val oct12 = LocalDate.of(2026, 10, 12)
    private val oct19 = LocalDate.of(2026, 10, 19)
    private val oct20 = LocalDate.of(2026, 10, 20)

    private val repository = ToolInventoryRepository()
    private val projection = ToolInventoryProjection(repository)

    private fun added(
        tool: ToolId,
        name: String,
        description: String? = null,
        days: Int = 7,
        at: Instant = now,
    ) = projection.on(ToolAdded(tool, name, description, days, at))

    private fun registered(
        neighbor: NeighborId,
        name: String,
    ) = projection.on(NeighborRegistered(neighbor, name, "x@example.org", now))

    private fun checkedOut(
        loan: LoanId,
        tool: ToolId,
        neighbor: NeighborId,
        due: LocalDate,
    ) = projection.on(ToolCheckedOut(loan, tool, neighbor, 7, due, now))

    private fun rows(on: LocalDate = today) = repository.rows(on)

    private fun rowOf(tool: ToolId) = rows().single { it.toolId == tool }

    // --- INV-TINV-1 and INV-TINV-4: one entry per Tool Added, with name, description, period ---

    @Test
    fun `INV-TINV-1 INV-TINV-4 INV-TINV-7 Tool Added drill is listed in the shed, retire offered, no mark lost`() {
        added(drill, "Cordless drill", "18V, two batteries", 7)
        val row = rowOf(drill)
        assertThat(row.name).isEqualTo("Cordless drill")
        assertThat(row.description).isEqualTo("18V, two batteries")
        assertThat(row.defaultCheckoutPeriodDays).isEqualTo(7)
        assertThat(row.addedAt).isEqualTo(now)
        assertThat(row.onLoan).isFalse()
        assertThat(row.canRetire).isTrue()
        assertThat(row.canMarkLost).isFalse()
        assertThat(row.retired).isFalse()
        assertThat(row.overdue).isFalse()
    }

    @Test
    fun `INV-TINV-4 given no tools, when Tool Added ladder without a description, then listed with 3 days and no description`() {
        added(ladder, "Ladder", null, 3)
        val row = rowOf(ladder)
        assertThat(row.description).isNull()
        assertThat(row.defaultCheckoutPeriodDays).isEqualTo(3)
    }

    @Test
    fun `INV-TINV-1 given one drill listed, when Tool Added for a second drill with its own id, then both are listed separately`() {
        added(drill, "Cordless drill")
        added(drill2, "Cordless drill")
        assertThat(rows().map { it.toolId }).containsExactly(drill, drill2)
    }

    // --- INV-TINV-8: applying the same event twice ---

    @Test
    fun `INV-TINV-8 given the drill listed once, when the same Tool Added is applied again, then still listed once`() {
        added(drill, "Cordless drill")
        added(drill, "Cordless drill")
        assertThat(rows()).hasSize(1)
    }

    @Test
    fun `INV-TINV-8 every event applied twice leaves the entry as it was after the first`() {
        added(drill, "Cordless drill")
        registered(ana, "Ana Ruiz")
        repeat(2) { checkedOut(l1, drill, ana, oct12) }
        repeat(2) { projection.on(LoanExtended(l1, oct12, oct19, now)) }
        val whileOut = rowOf(drill)
        assertThat(whileOut.dueDate).isEqualTo(oct19)
        assertThat(whileOut.borrowerName).isEqualTo("Ana Ruiz")
        repeat(2) { projection.on(ToolReturned(l1, drill, now, today, false)) }
        assertThat(rowOf(drill).onLoan).isFalse()
        // a replayed Check Out after the return does not put the tool back out
        checkedOut(l1, drill, ana, oct12)
        assertThat(rowOf(drill).onLoan).isFalse()
        repeat(2) { projection.on(ToolRetired(drill, now)) }
        assertThat(rows()).isEmpty()
        // a replayed Tool Added does not bring a dropped tool back
        added(drill, "Cordless drill")
        assertThat(rows()).isEmpty()
        repeat(2) { projection.on(ToolLost(l1, drill, now)) }
        assertThat(rows()).isEmpty()
    }

    // --- INV-TINV-3: neighbor registers changes no entry ---

    @Test
    fun `INV-TINV-3 given the drill in the shed, when Neighbor Registered Ana Ruiz, then no entry changes`() {
        added(drill, "Cordless drill")
        val before = rows()
        registered(ana, "Ana Ruiz")
        assertThat(rows()).isEqualTo(before)
    }

    // --- INV-TINV-5 and INV-TINV-6: out on loan ---

    @Test
    fun `INV-TINV-5 INV-TINV-6 Tool Checked Out due 12 October shows out on loan to Ana Ruiz, mark lost offered`() {
        added(drill, "Cordless drill")
        registered(ana, "Ana Ruiz")
        checkedOut(l1, drill, ana, oct12)
        val row = rowOf(drill)
        assertThat(row.onLoan).isTrue()
        assertThat(row.loanId).isEqualTo(l1)
        assertThat(row.borrowerId).isEqualTo(ana)
        assertThat(row.borrowerName).isEqualTo("Ana Ruiz")
        assertThat(row.dueDate).isEqualTo(oct12)
        assertThat(row.canMarkLost).isTrue()
    }

    @Test
    fun `INV-TINV-5 given the drill out to Ana due 12 October, when Loan Extended to 19 October, then due 19 October`() {
        added(drill, "Cordless drill")
        registered(ana, "Ana Ruiz")
        checkedOut(l1, drill, ana, oct12)
        projection.on(LoanExtended(l1, oct12, oct19, now))
        val row = rowOf(drill)
        assertThat(row.borrowerName).isEqualTo("Ana Ruiz")
        assertThat(row.dueDate).isEqualTo(oct19)
    }

    @Test
    fun `INV-TINV-5 INV-TINV-6 Tool Returned puts the drill in the shed with no borrower, due date or mark lost`() {
        added(drill, "Cordless drill")
        registered(ana, "Ana Ruiz")
        checkedOut(l1, drill, ana, oct12)
        projection.on(LoanExtended(l1, oct12, oct19, now))
        projection.on(ToolReturned(l1, drill, now, today, false))
        val row = rowOf(drill)
        assertThat(row.onLoan).isFalse()
        assertThat(row.loanId).isNull()
        assertThat(row.borrowerId).isNull()
        assertThat(row.borrowerName).isNull()
        assertThat(row.dueDate).isNull()
        assertThat(row.canMarkLost).isFalse()
        assertThat(row.overdue).isFalse()
    }

    // --- INV-TINV-2, INV-TINV-6, INV-TINV-7: retired and lost ---

    @Test
    fun `INV-TINV-2 Tool Retired in the shed drops the drill off and the ladder is still listed`() {
        added(drill, "Cordless drill")
        added(ladder, "Ladder")
        projection.on(ToolRetired(drill, now))
        assertThat(rows().map { it.toolId }).containsExactly(ladder)
    }

    @Test
    fun `INV-TINV-2 INV-TINV-6 INV-TINV-7 Tool Retired while out stays listed, retired and out, mark lost offered, no retire`() {
        added(ladder, "Ladder")
        registered(ben, "Ben Okafor")
        checkedOut(l2, ladder, ben, oct20)
        projection.on(ToolRetired(ladder, now))
        val row = rowOf(ladder)
        assertThat(row.retired).isTrue()
        assertThat(row.onLoan).isTrue()
        assertThat(row.borrowerName).isEqualTo("Ben Okafor")
        assertThat(row.dueDate).isEqualTo(oct20)
        assertThat(row.canMarkLost).isTrue()
        assertThat(row.canRetire).isFalse()
    }

    @Test
    fun `INV-TINV-2 given the ladder retired and out to Ben, when Tool Returned, then the ladder drops off`() {
        added(ladder, "Ladder")
        registered(ben, "Ben Okafor")
        checkedOut(l2, ladder, ben, oct20)
        projection.on(ToolRetired(ladder, now))
        projection.on(ToolReturned(l2, ladder, now, today, false))
        assertThat(rows()).isEmpty()
    }

    @Test
    fun `INV-TINV-2 given the ladder retired and out to Ben, when Tool Lost, then the ladder drops off`() {
        added(ladder, "Ladder")
        registered(ben, "Ben Okafor")
        checkedOut(l2, ladder, ben, oct20)
        projection.on(ToolRetired(ladder, now))
        projection.on(ToolLost(l2, ladder, now))
        assertThat(rows()).isEmpty()
    }

    @Test
    fun `INV-TINV-2 given the hedge trimmer out to Ana, when Tool Lost, then the hedge trimmer drops off`() {
        added(trimmer, "Hedge trimmer")
        registered(ana, "Ana Ruiz")
        checkedOut(l1, trimmer, ana, oct12)
        projection.on(ToolLost(l1, trimmer, now))
        assertThat(rows()).isEmpty()
    }

    @Test
    fun `INV-TINV-2 an event for a tool that is no longer listed changes nothing`() {
        added(drill, "Cordless drill")
        added(ladder, "Ladder")
        projection.on(ToolRetired(drill, now))
        projection.on(LoanExtended(l1, oct12, oct19, now))
        projection.on(ToolReturned(l1, drill, now, today, false))
        projection.on(ToolLost(l1, drill, now))
        assertThat(rows().map { it.toolId }).containsExactly(ladder)
    }

    // --- INV-TINV-9: overdue is worked out on read ---

    @Test
    fun `INV-TINV-9 drill due 12 October is not overdue on 12 October and overdue on 13 October`() {
        added(drill, "Cordless drill")
        registered(ana, "Ana Ruiz")
        checkedOut(l1, drill, ana, oct12)
        assertThat(rows(on = oct12).single().overdue).isFalse()
        assertThat(rows(on = oct12.plusDays(1)).single().overdue).isTrue()
    }

    @Test
    fun `INV-TINV-9 a tool in the shed is never overdue`() {
        added(drill, "Cordless drill")
        assertThat(rows(on = oct12.plusDays(30)).single().overdue).isFalse()
    }

    // --- INV-TINV-10: list order ---

    @Test
    fun `INV-TINV-10 given Ladder then Cordless drill then a second Cordless drill added, then the order is drill, drill, ladder`() {
        added(ladder, "Ladder")
        added(drill, "Cordless drill")
        added(drill2, "Cordless drill")
        assertThat(rows().map { it.toolId }).containsExactly(drill, drill2, ladder)
    }

    @Test
    fun `INV-TINV-10 names are ordered ignoring case, then by addedAt, then by the order recorded`() {
        added(ladder, "ladder", at = now)
        added(drill2, "CORDLESS drill", at = now.plusSeconds(5))
        added(drill, "Cordless drill", at = now)
        assertThat(rows().map { it.toolId }).containsExactly(drill, drill2, ladder)
    }

    // --- INV-TINV-11 and INV-TINV-12: who may read ---

    @Test
    fun `INV-TINV-11 a request with no sign-in is refused not-signed-in before anything else`() {
        assertThatThrownBy { Caller.ANONYMOUS.requireToolInventoryReader() }
            .hasMessage("Please sign in.")
            .hasFieldOrPropertyWithValue("invariant", "INV-TINV-11")
            .hasFieldOrPropertyWithValue("reason", NOT_SIGNED_IN)
    }

    @Test
    fun `INV-TINV-12 a neighbor without the shed-manager role is refused not-shed-manager`() {
        assertThatThrownBy { Caller(neighborId = ana, isShedManager = false).requireToolInventoryReader() }
            .hasMessage("Only the shed manager can see the tool inventory.")
            .hasFieldOrPropertyWithValue("invariant", "INV-TINV-12")
            .hasFieldOrPropertyWithValue("reason", NOT_SHED_MANAGER)
    }

    @Test
    fun `INV-TINV-12 the shed manager is let through`() {
        Caller(neighborId = null, isShedManager = true).requireToolInventoryReader()
    }
}
