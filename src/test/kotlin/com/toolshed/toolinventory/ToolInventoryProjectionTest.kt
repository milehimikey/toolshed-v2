package com.toolshed.toolinventory

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The view's rules, applied to the in-memory read model. */
class ToolInventoryProjectionTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val drill2 = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d2"))
    private val ladder = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d3"))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"))
    private val l1 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))
    private val l2 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000012"))
    private val oct12 = LocalDate.of(2026, 10, 12)
    private val oct19 = LocalDate.of(2026, 10, 19)
    private val oct20 = LocalDate.of(2026, 10, 20)

    private fun ToolInventoryRepository.add(
        tool: ToolId,
        name: String,
        description: String? = null,
        days: Int = 7,
        at: Instant = now,
    ) = addTool(tool, name, description, days, at)

    private fun ToolInventoryRepository.read(today: LocalDate = LocalDate.of(2026, 10, 7)) = rows { today.isAfter(it) }

    private fun ToolInventoryRepository.rowOf(tool: ToolId) = read().single { it.toolId == tool }

    // --- INV-TINV-1 ---

    @Test
    fun `INV-TINV-1 INV-TINV-4 INV-TINV-7 tool added is listed once with its details, in the shed, retire offered and no mark lost`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill", "18V, two batteries", 7)
        val row = r.rowOf(drill)
        assertThat(row.name).isEqualTo("Cordless drill")
        assertThat(row.description).isEqualTo("18V, two batteries")
        assertThat(row.defaultCheckoutPeriodDays).isEqualTo(7)
        assertThat(row.addedAt).isEqualTo(now)
        assertThat(row.onLoan).isFalse()
        assertThat(row.canRetire).isTrue()
        assertThat(row.canMarkLost).isFalse()
        assertThat(row.retired).isFalse()
    }

    @Test
    fun `INV-TINV-4 tool added without a description lists no description`() {
        val r = ToolInventoryRepository()
        r.add(ladder, "Ladder", null, 3)
        val row = r.rowOf(ladder)
        assertThat(row.description).isNull()
        assertThat(row.defaultCheckoutPeriodDays).isEqualTo(3)
    }

    @Test
    fun `INV-TINV-1 two of the same tool are two entries`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        r.add(drill2, "Cordless drill")
        assertThat(r.read()).hasSize(2)
    }

    // --- INV-TINV-8 ---

    @Test
    fun `INV-TINV-8 same Tool Added applied twice lists the drill once`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        r.add(drill, "Cordless drill")
        assertThat(r.read()).hasSize(1)
    }

    @Test
    fun `INV-TINV-8 every event applied twice leaves the entry as after the first`() {
        val r = ToolInventoryRepository()
        r.registerNeighbor(ana, "Ana Ruiz")
        r.add(drill, "Cordless drill")
        repeat(2) { r.checkOut(l1, drill, ana, oct12) }
        repeat(2) { r.extend(l1, oct19) }
        val onLoan = r.rowOf(drill)
        assertThat(onLoan.dueDate).isEqualTo(oct19)
        assertThat(onLoan.onLoan).isTrue()
        repeat(2) { r.endLoan(l1) }
        assertThat(r.rowOf(drill).onLoan).isFalse()
        repeat(2) { r.retire(drill) }
        assertThat(r.read()).isEmpty()
        r.add(drill, "Cordless drill")
        assertThat(r.read()).isEmpty()
        repeat(2) { r.recordLost(drill) }
        assertThat(r.read()).isEmpty()
    }

    // --- INV-TINV-3 ---

    @Test
    fun `INV-TINV-3 neighbor registers and no entry changes`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        val before = r.read()
        r.registerNeighbor(ana, "Ana Ruiz")
        assertThat(r.read()).isEqualTo(before)
    }

    // --- INV-TINV-5, INV-TINV-6 ---

    @Test
    fun `INV-TINV-5 INV-TINV-6 tool checked out shows borrower name and due date and offers mark lost`() {
        val r = ToolInventoryRepository()
        r.registerNeighbor(ana, "Ana Ruiz")
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        val row = r.rowOf(drill)
        assertThat(row.onLoan).isTrue()
        assertThat(row.loanId).isEqualTo(l1)
        assertThat(row.borrowerId).isEqualTo(ana)
        assertThat(row.borrowerName).isEqualTo("Ana Ruiz")
        assertThat(row.dueDate).isEqualTo(oct12)
        assertThat(row.canMarkLost).isTrue()
    }

    @Test
    fun `INV-TINV-5 loan extended moves the due date`() {
        val r = ToolInventoryRepository()
        r.registerNeighbor(ana, "Ana Ruiz")
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        r.extend(l1, oct19)
        assertThat(r.rowOf(drill).dueDate).isEqualTo(oct19)
    }

    @Test
    fun `INV-TINV-5 INV-TINV-6 tool returned shows in the shed with no borrower, due date or mark lost`() {
        val r = ToolInventoryRepository()
        r.registerNeighbor(ana, "Ana Ruiz")
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        r.extend(l1, oct19)
        r.endLoan(l1)
        val row = r.rowOf(drill)
        assertThat(row.onLoan).isFalse()
        assertThat(row.loanId).isNull()
        assertThat(row.borrowerId).isNull()
        assertThat(row.borrowerName).isNull()
        assertThat(row.dueDate).isNull()
        assertThat(row.canMarkLost).isFalse()
    }

    // --- INV-TINV-2, INV-TINV-7 ---

    @Test
    fun `INV-TINV-2 tool retired in the shed drops off and the other tool is still listed`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        r.add(ladder, "Ladder")
        r.retire(drill)
        assertThat(r.read().map { it.toolId }).containsExactly(ladder)
    }

    @Test
    fun `INV-TINV-2 INV-TINV-6 INV-TINV-7 tool retired while out stays listed, retired and out, mark lost offered and retire not`() {
        val r = ToolInventoryRepository()
        r.registerNeighbor(ben, "Ben")
        r.add(ladder, "Ladder")
        r.checkOut(l2, ladder, ben, oct20)
        r.retire(ladder)
        val row = r.rowOf(ladder)
        assertThat(row.retired).isTrue()
        assertThat(row.onLoan).isTrue()
        assertThat(row.borrowerName).isEqualTo("Ben")
        assertThat(row.dueDate).isEqualTo(oct20)
        assertThat(row.canMarkLost).isTrue()
        assertThat(row.canRetire).isFalse()
    }

    @Test
    fun `INV-TINV-2 retired tool that comes back drops off`() {
        val r = ToolInventoryRepository()
        r.registerNeighbor(ben, "Ben")
        r.add(ladder, "Ladder")
        r.checkOut(l2, ladder, ben, oct20)
        r.retire(ladder)
        r.endLoan(l2)
        assertThat(r.read()).isEmpty()
    }

    @Test
    fun `INV-TINV-2 retired tool recorded lost drops off`() {
        val r = ToolInventoryRepository()
        r.registerNeighbor(ben, "Ben")
        r.add(ladder, "Ladder")
        r.checkOut(l2, ladder, ben, oct20)
        r.retire(ladder)
        r.recordLost(ladder)
        assertThat(r.read()).isEmpty()
    }

    @Test
    fun `INV-TINV-2 tool recorded lost drops off`() {
        val r = ToolInventoryRepository()
        r.registerNeighbor(ana, "Ana Ruiz")
        r.add(drill, "Hedge trimmer")
        r.checkOut(l1, drill, ana, oct12)
        r.recordLost(drill)
        assertThat(r.read()).isEmpty()
    }

    // --- INV-TINV-9 ---

    @Test
    fun `INV-TINV-9 out tool is not overdue on its due date and overdue the day after`() {
        val r = ToolInventoryRepository()
        r.registerNeighbor(ana, "Ana Ruiz")
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        assertThat(r.read(today = oct12).single().overdue).isFalse()
        assertThat(r.read(today = LocalDate.of(2026, 10, 13)).single().overdue).isTrue()
    }

    @Test
    fun `INV-TINV-9 a tool in the shed is never overdue`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        assertThat(r.read(today = LocalDate.of(2030, 1, 1)).single().overdue).isFalse()
    }

    // --- INV-TINV-10 ---

    @Test
    fun `INV-TINV-10 list is by name ignoring case, then added time, then order recorded`() {
        val r = ToolInventoryRepository()
        r.add(ladder, "Ladder")
        r.add(drill, "Cordless drill")
        r.add(drill2, "cordless drill")
        assertThat(r.read().map { it.toolId }).containsExactly(drill, drill2, ladder)
    }

    @Test
    fun `INV-TINV-10 earlier added time sorts first among the same name`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill", at = now.plusSeconds(10))
        r.add(drill2, "Cordless drill", at = now)
        assertThat(r.read().map { it.toolId }).containsExactly(drill2, drill)
    }

    // --- Listing ---

    @Test
    fun `listing the inventory returns both tools with all their fields`() {
        val r = ToolInventoryRepository()
        r.registerNeighbor(ben, "Ben")
        r.add(drill, "Cordless drill")
        r.add(ladder, "Ladder")
        r.checkOut(l2, ladder, ben, oct20)
        val rows = r.read()
        assertThat(rows).hasSize(2)
        assertThat(rows[0].onLoan).isFalse()
        assertThat(rows[1].borrowerName).isEqualTo("Ben")
        assertThat(rows[1].dueDate).isEqualTo(oct20)
    }
}
