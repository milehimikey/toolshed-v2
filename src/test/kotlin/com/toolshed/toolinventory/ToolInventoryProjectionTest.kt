package com.toolshed.toolinventory

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The view's rules, applied to the in-memory read model: one row per added tool, loans, retirement, order. */
class ToolInventoryProjectionTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val ladder = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d2"))
    private val trimmer = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d3"))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"))
    private val l1 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))
    private val l2 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000012"))
    private val oct12 = LocalDate.of(2026, 10, 12)
    private val oct19 = LocalDate.of(2026, 10, 19)
    private val today = LocalDate.of(2026, 10, 6)

    private fun ToolInventoryRepository.add(
        tool: ToolId,
        name: String,
        description: String? = null,
        days: Int = 7,
        at: Instant = now,
    ) = addTool(tool, name, description, days, at)

    private fun ToolInventoryRepository.rowOf(
        tool: ToolId,
        on: LocalDate = today,
    ) = rows(on).single { it.toolId == tool }

    private fun withNeighbors() =
        ToolInventoryRepository().also {
            it.registerNeighbor(ana, "Ana Ruiz")
            it.registerNeighbor(ben, "Ben Cole")
        }

    // --- INV-TINV-1 / INV-TINV-4 / INV-TINV-7: added tools ---

    @Test
    fun `INV-TINV-1 INV-TINV-4 INV-TINV-7 Tool added - listed with description and period, in the shed, retire offered, no mark lost`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill", "18V, two batteries", 7)
        val row = r.rowOf(drill)
        assertThat(row.description).isEqualTo("18V, two batteries")
        assertThat(row.defaultCheckoutPeriodDays).isEqualTo(7)
        assertThat(row.onLoan).isFalse()
        assertThat(row.loanId).isNull()
        assertThat(row.borrowerName).isNull()
        assertThat(row.dueDate).isNull()
        assertThat(row.retired).isFalse()
        assertThat(row.canRetire).isTrue()
        assertThat(row.canMarkLost).isFalse()
        assertThat(row.overdue).isFalse()
    }

    @Test
    fun `INV-TINV-4 Tool added without a description - listed with its period and no description`() {
        val r = ToolInventoryRepository()
        r.add(ladder, "Ladder", null, 3)
        val row = r.rowOf(ladder)
        assertThat(row.description).isNull()
        assertThat(row.defaultCheckoutPeriodDays).isEqualTo(3)
    }

    @Test
    fun `INV-TINV-1 Two of the same tool - two separate entries`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        r.add(ladder, "Cordless drill")
        assertThat(r.rows(today)).hasSize(2)
    }

    // --- INV-TINV-8: idempotency ---

    @Test
    fun `INV-TINV-8 Same event applied twice - Tool Added leaves one entry`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        r.add(drill, "Cordless drill")
        assertThat(r.rows(today)).hasSize(1)
    }

    @Test
    fun `INV-TINV-8 Same event applied twice - every other event leaves the entry as after the first`() {
        val r = withNeighbors()
        r.add(drill, "Drill")
        r.add(ladder, "Ladder")
        repeat(2) { r.checkOut(l1, drill, ana, oct12) }
        repeat(2) { r.extend(l1, oct19) }
        assertThat(r.rowOf(drill).dueDate).isEqualTo(oct19)
        repeat(2) { r.retire(drill) }
        assertThat(r.rowOf(drill).retired).isTrue()
        repeat(2) { r.endLoan(l1) }
        assertThat(r.rows(today).map { it.toolId }).containsExactly(ladder)
        repeat(2) { r.checkOut(l1, drill, ana, oct12) } // replay after drop-off
        repeat(2) { r.add(drill, "Drill") }
        assertThat(r.rows(today).map { it.toolId }).containsExactly(ladder)
        repeat(2) { r.recordLost(l2, ladder) }
        assertThat(r.rows(today)).isEmpty()
    }

    // --- INV-TINV-3: only the seven events change it ---

    @Test
    fun `INV-TINV-3 Neighbor registers - no entry changes`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        val before = r.rows(today)
        r.registerNeighbor(ana, "Ana Ruiz")
        assertThat(r.rows(today)).isEqualTo(before)
    }

    // --- INV-TINV-5 / INV-TINV-6: loans ---

    @Test
    fun `INV-TINV-5 INV-TINV-6 Tool checked out - out on loan to the registered name, due date, mark lost offered`() {
        val r = withNeighbors()
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        val row = r.rowOf(drill)
        assertThat(row.onLoan).isTrue()
        assertThat(row.loanId).isEqualTo(l1)
        assertThat(row.borrowerId).isEqualTo(ana)
        assertThat(row.borrowerName).isEqualTo("Ana Ruiz")
        assertThat(row.dueDate).isEqualTo(oct12)
        assertThat(row.canMarkLost).isTrue()
        assertThat(row.canRetire).isTrue()
    }

    @Test
    fun `INV-TINV-5 Loan extended - the due date moves`() {
        val r = withNeighbors()
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        r.extend(l1, oct19)
        assertThat(r.rowOf(drill).dueDate).isEqualTo(oct19)
    }

    @Test
    fun `INV-TINV-5 INV-TINV-6 Tool returned - in the shed, no borrower, due date or mark lost`() {
        val r = withNeighbors()
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct19)
        r.endLoan(l1)
        val row = r.rowOf(drill)
        assertThat(row.onLoan).isFalse()
        assertThat(row.borrowerId).isNull()
        assertThat(row.borrowerName).isNull()
        assertThat(row.dueDate).isNull()
        assertThat(row.loanId).isNull()
        assertThat(row.canMarkLost).isFalse()
    }

    // --- INV-TINV-2: retire and lost ---

    @Test
    fun `INV-TINV-2 Tool retired in the shed - drops off, the other tool stays`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        r.add(ladder, "Ladder")
        r.retire(drill)
        assertThat(r.rows(today).map { it.toolId }).containsExactly(ladder)
    }

    @Test
    fun `INV-TINV-2 INV-TINV-6 INV-TINV-7 Tool retired while out on loan - stays listed, retired and out, mark lost kept, retire gone`() {
        val r = withNeighbors()
        r.add(ladder, "Ladder")
        r.checkOut(l1, ladder, ben, LocalDate.of(2026, 10, 20))
        r.retire(ladder)
        val row = r.rowOf(ladder)
        assertThat(row.retired).isTrue()
        assertThat(row.onLoan).isTrue()
        assertThat(row.borrowerName).isEqualTo("Ben Cole")
        assertThat(row.dueDate).isEqualTo(LocalDate.of(2026, 10, 20))
        assertThat(row.canMarkLost).isTrue()
        assertThat(row.canRetire).isFalse()
    }

    @Test
    fun `INV-TINV-2 Retired tool comes back - drops off`() {
        val r = withNeighbors()
        r.add(ladder, "Ladder")
        r.checkOut(l1, ladder, ben, oct12)
        r.retire(ladder)
        r.endLoan(l1)
        assertThat(r.rows(today)).isEmpty()
    }

    @Test
    fun `INV-TINV-2 Retired tool recorded lost - drops off`() {
        val r = withNeighbors()
        r.add(ladder, "Ladder")
        r.checkOut(l1, ladder, ben, oct12)
        r.retire(ladder)
        r.recordLost(l1, ladder)
        assertThat(r.rows(today)).isEmpty()
    }

    @Test
    fun `INV-TINV-2 Tool recorded lost - drops off`() {
        val r = withNeighbors()
        r.add(trimmer, "Hedge trimmer")
        r.checkOut(l1, trimmer, ana, oct12)
        r.recordLost(l1, trimmer)
        assertThat(r.rows(today)).isEmpty()
    }

    // --- INV-TINV-9: overdue ---

    @Test
    fun `INV-TINV-9 Out tool becomes overdue - not overdue through the due date, overdue the day after`() {
        val r = withNeighbors()
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        assertThat(r.rowOf(drill, on = oct12).overdue).isFalse()
        assertThat(r.rowOf(drill, on = oct12.plusDays(1)).overdue).isTrue()
    }

    @Test
    fun `INV-TINV-9 a tool in the shed is never overdue`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        assertThat(r.rowOf(drill, on = LocalDate.of(2030, 1, 1)).overdue).isFalse()
    }

    // --- INV-TINV-10: order ---

    @Test
    fun `INV-TINV-10 List order - by name ignoring case, then added time, then recorded order`() {
        val r = ToolInventoryRepository()
        r.add(ladder, "Ladder", at = now)
        r.add(drill, "cordless drill", at = now)
        r.add(trimmer, "Cordless drill", at = now)
        assertThat(r.rows(today).map { it.toolId }).containsExactly(drill, trimmer, ladder)
    }

    @Test
    fun `INV-TINV-10 same name - earlier addedAt first even when recorded later`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Drill", at = now.plusSeconds(5))
        r.add(trimmer, "Drill", at = now)
        assertThat(r.rows(today).map { it.toolId }).containsExactly(trimmer, drill)
    }

    // --- Listing ---

    @Test
    fun `Listing the inventory - both tools returned with all fields`() {
        val r = withNeighbors()
        r.add(drill, "Cordless drill")
        r.add(ladder, "Ladder")
        r.checkOut(l1, ladder, ben, LocalDate.of(2026, 10, 20))
        val rows = r.rows(today)
        assertThat(rows.map { it.name }).containsExactly("Cordless drill", "Ladder")
        assertThat(rows[1].borrowerName).isEqualTo("Ben Cole")
    }
}
