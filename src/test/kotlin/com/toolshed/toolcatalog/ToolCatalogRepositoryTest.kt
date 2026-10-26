package com.toolshed.toolcatalog

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** The view's status rules, applied to the in-memory read model: one row per added tool, holds, loans, order. */
class ToolCatalogRepositoryTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val ladder = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d2"))
    private val trimmer = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d3"))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"))
    private val cara = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a3"))
    private val l1 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))
    private val l2 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000012"))
    private val h1 = HoldId(UUID.fromString("00000000-0000-0000-0000-000000000021"))
    private val h2 = HoldId(UUID.fromString("00000000-0000-0000-0000-000000000022"))
    private val oct12 = LocalDate.of(2026, 10, 12)
    private val oct19 = LocalDate.of(2026, 10, 19)

    private fun ToolCatalogRepository.add(
        tool: ToolId,
        name: String,
        description: String? = null,
        days: Int = 7,
        at: Instant = now,
    ) = addTool(tool, name, description, days, at)

    private fun ToolCatalogRepository.rowOf(tool: ToolId) = rows().single { it.toolId == tool }

    // --- INV-TCAT-1: every added tool appears exactly once, available ---

    @Test
    fun `INV-TCAT-1 a tool added is listed once, available, with no holds waiting, offering check out and not place hold`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill", description = "18V, two batteries", days = 7)
        val row = r.rowOf(drill)
        assertThat(row.description).isEqualTo("18V, two batteries")
        assertThat(row.defaultCheckoutPeriodDays).isEqualTo(7)
        assertThat(row.status).isEqualTo(ToolCatalogStatus.AVAILABLE)
        assertThat(row.holdsWaiting).isZero()
        assertThat(row.canCheckOut).isTrue()
        assertThat(row.canPlaceHold).isFalse()
    }

    @Test
    fun `INV-TCAT-1 two identically named tools are two separate entries`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.add(ladder, "Cordless drill")
        assertThat(r.rows()).hasSize(2)
    }

    // --- INV-TCAT-12: a replayed event leaves the entry as it was after the first ---

    @Test
    fun `INV-TCAT-12 the same Tool Added and the same Hold Placed applied twice leave one entry with 1 hold waiting`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.add(drill, "Cordless drill")
        r.placeHold(h1, drill, ana)
        r.placeHold(h1, drill, ana)
        assertThat(r.rows()).hasSize(1)
        assertThat(r.rowOf(drill).holdsWaiting).isEqualTo(1)
    }

    // --- INV-TCAT-2 and INV-TCAT-11: checked out shows due date, extends, clears on return ---

    @Test
    fun `INV-TCAT-2 INV-TCAT-11 a checked-out tool shows checked-out and its due date, offers place hold not check out`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        val row = r.rowOf(drill)
        assertThat(row.status).isEqualTo(ToolCatalogStatus.CHECKED_OUT)
        assertThat(row.dueDate).isEqualTo(oct12)
        assertThat(row.canCheckOut).isFalse()
        assertThat(row.canPlaceHold).isTrue()
    }

    @Test
    fun `INV-TCAT-2 the due date moves with each loan extension`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        r.extend(l1, oct19)
        assertThat(r.rowOf(drill).dueDate).isEqualTo(oct19)
    }

    @Test
    fun `INV-TCAT-2 a tool returned with no holds waiting is available with no due date`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct19)
        r.endLoan(l1)
        val row = r.rowOf(drill)
        assertThat(row.status).isEqualTo(ToolCatalogStatus.AVAILABLE)
        assertThat(row.dueDate).isNull()
    }

    // --- INV-TCAT-3: back in the shed with holds waiting, but not set aside, shows reserved ---

    @Test
    fun `INV-TCAT-3 INV-TCAT-7 INV-TCAT-11 a tool returned with holds waiting is reserved, offering both actions`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        r.placeHold(h1, drill, ben)
        r.placeHold(h2, drill, cara)
        r.endLoan(l1)
        val row = r.rowOf(drill)
        assertThat(row.status).isEqualTo(ToolCatalogStatus.RESERVED)
        assertThat(row.dueDate).isNull()
        assertThat(row.holdsWaiting).isEqualTo(2)
        assertThat(row.canCheckOut).isTrue()
        assertThat(row.canPlaceHold).isTrue()
    }

    @Test
    fun `INV-TCAT-3 INV-TCAT-6 cancelling the only waiting hold while reserved leaves the tool available`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        r.placeHold(h1, drill, ben)
        r.endLoan(l1)
        assertThat(r.rowOf(drill).status).isEqualTo(ToolCatalogStatus.RESERVED)
        r.cancelHold(h1)
        val row = r.rowOf(drill)
        assertThat(row.status).isEqualTo(ToolCatalogStatus.AVAILABLE)
        assertThat(row.holdsWaiting).isZero()
    }

    // --- INV-TCAT-4: set aside until collected or the set-aside ends ---

    @Test
    fun `INV-TCAT-4 fulfilling the front hold shows set aside, still offering both actions`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.placeHold(h1, drill, ben)
        r.placeHold(h2, drill, cara)
        r.fulfillHold(h1, drill)
        val row = r.rowOf(drill)
        assertThat(row.status).isEqualTo(ToolCatalogStatus.SET_ASIDE)
        assertThat(row.holdsWaiting).isEqualTo(1)
        assertThat(row.canCheckOut).isTrue()
        assertThat(row.canPlaceHold).isTrue()
    }

    @Test
    fun `INV-TCAT-2 INV-TCAT-4 the holder collecting the set-aside tool shows checked out, holds waiting unchanged`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.placeHold(h1, drill, ben)
        r.placeHold(h2, drill, cara)
        r.fulfillHold(h1, drill)
        r.checkOut(l1, drill, ben, LocalDate.of(2026, 10, 24))
        val row = r.rowOf(drill)
        assertThat(row.status).isEqualTo(ToolCatalogStatus.CHECKED_OUT)
        assertThat(row.dueDate).isEqualTo(LocalDate.of(2026, 10, 24))
        assertThat(row.holdsWaiting).isEqualTo(1)
    }

    @Test
    fun `INV-TCAT-2 INV-TCAT-6 the front holder collecting before the set-aside is recorded ends that hold`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.placeHold(h1, drill, ben)
        r.placeHold(h2, drill, cara)
        r.checkOut(l1, drill, ben, LocalDate.of(2026, 10, 24))
        val row = r.rowOf(drill)
        assertThat(row.status).isEqualTo(ToolCatalogStatus.CHECKED_OUT)
        assertThat(row.dueDate).isEqualTo(LocalDate.of(2026, 10, 24))
        assertThat(row.holdsWaiting).isEqualTo(1)
    }

    @Test
    fun `INV-TCAT-3 INV-TCAT-4 a set-aside expiring with another hold waiting shows reserved, then set aside once fulfilled`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.placeHold(h1, drill, ben)
        r.placeHold(h2, drill, cara)
        r.fulfillHold(h1, drill)
        r.expireHold(h1)
        val reserved = r.rowOf(drill)
        assertThat(reserved.status).isEqualTo(ToolCatalogStatus.RESERVED)
        assertThat(reserved.holdsWaiting).isEqualTo(1)

        r.fulfillHold(h2, drill)
        val setAside = r.rowOf(drill)
        assertThat(setAside.status).isEqualTo(ToolCatalogStatus.SET_ASIDE)
        assertThat(setAside.holdsWaiting).isZero()
    }

    @Test
    fun `INV-TCAT-2 INV-TCAT-4 a lapsed set-aside taken by someone else before the expiry lands never shows available or reserved`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.placeHold(h1, drill, ben) // Ben's hold, set aside for him
        r.placeHold(h2, drill, cara) // Cara's hold, waiting
        r.fulfillHold(h1, drill)

        r.checkOut(l2, drill, cara, LocalDate.of(2026, 10, 25))
        val afterCheckout = r.rowOf(drill)
        assertThat(afterCheckout.status).isEqualTo(ToolCatalogStatus.CHECKED_OUT)
        assertThat(afterCheckout.dueDate).isEqualTo(LocalDate.of(2026, 10, 25))
        assertThat(afterCheckout.holdsWaiting).isZero()

        r.expireHold(h1)
        val afterExpiry = r.rowOf(drill)
        assertThat(afterExpiry.status).isEqualTo(ToolCatalogStatus.CHECKED_OUT)
        assertThat(afterExpiry.dueDate).isEqualTo(LocalDate.of(2026, 10, 25))
    }

    @Test
    fun `INV-TCAT-4 a set-aside expiring with no hold waiting shows available`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.placeHold(h1, drill, ben)
        r.fulfillHold(h1, drill)
        r.expireHold(h1)
        assertThat(r.rowOf(drill).status).isEqualTo(ToolCatalogStatus.AVAILABLE)
    }

    @Test
    fun `INV-TCAT-3 INV-TCAT-4 cancelling a set-aside hold with another hold waiting shows reserved`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.placeHold(h1, drill, ben)
        r.placeHold(h2, drill, cara)
        r.fulfillHold(h1, drill)
        r.cancelHold(h1)
        val row = r.rowOf(drill)
        assertThat(row.status).isEqualTo(ToolCatalogStatus.RESERVED)
        assertThat(row.holdsWaiting).isEqualTo(1)
    }

    @Test
    fun `INV-TCAT-4 the set-aside holder closing their account with no hold waiting shows available`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.placeHold(h1, drill, cara)
        r.fulfillHold(h1, drill)
        r.closeAccount(cara)
        assertThat(r.rowOf(drill).status).isEqualTo(ToolCatalogStatus.AVAILABLE)
    }

    // --- INV-TCAT-6: holds waiting, including when a waiting holder closes their account ---

    @Test
    fun `INV-TCAT-6 cancelling one of two waiting holds while checked out leaves 1 hold waiting`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        r.placeHold(h1, drill, ben)
        r.placeHold(h2, drill, cara)
        r.cancelHold(h1)
        val row = r.rowOf(drill)
        assertThat(row.status).isEqualTo(ToolCatalogStatus.CHECKED_OUT)
        assertThat(row.holdsWaiting).isEqualTo(1)
    }

    @Test
    fun `INV-TCAT-6 a waiting holder closing their account removes only their holds, tools stay checked out`() {
        val r = ToolCatalogRepository()
        val hedgeTrimmer = trimmer
        val l3 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000013"))
        val h3 = HoldId(UUID.fromString("00000000-0000-0000-0000-000000000023"))
        r.add(drill, "Cordless drill")
        r.add(hedgeTrimmer, "Hedge trimmer")
        r.checkOut(l1, drill, ana, oct12)
        r.checkOut(l3, hedgeTrimmer, ben, oct12)
        r.placeHold(h1, drill, ben)
        r.placeHold(h2, drill, cara)
        r.placeHold(h3, hedgeTrimmer, cara)

        r.closeAccount(cara)

        val drillRow = r.rowOf(drill)
        val trimmerRow = r.rowOf(hedgeTrimmer)
        assertThat(drillRow.status).isEqualTo(ToolCatalogStatus.CHECKED_OUT)
        assertThat(drillRow.holdsWaiting).isEqualTo(1)
        assertThat(trimmerRow.status).isEqualTo(ToolCatalogStatus.CHECKED_OUT)
        assertThat(trimmerRow.holdsWaiting).isZero()
    }

    // --- INV-TCAT-8: a retired tool drops off at once, including while on loan; later events never bring it back ---

    @Test
    fun `INV-TCAT-8 a tool retired in the shed drops off, the other tool stays`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.add(ladder, "Ladder")
        r.retire(drill)
        assertThat(r.rows().map { it.toolId }).containsExactly(ladder)
    }

    @Test
    fun `INV-TCAT-8 a tool retired while on loan drops off at once, and a later return never brings it back`() {
        val r = ToolCatalogRepository()
        r.add(trimmer, "Hedge trimmer")
        r.checkOut(l1, trimmer, ana, oct12)
        r.placeHold(h1, trimmer, ben)
        r.retire(trimmer)
        assertThat(r.rows()).isEmpty()
        r.endLoan(l1)
        assertThat(r.rows()).isEmpty()
    }

    // --- INV-TCAT-10: a tool recorded lost drops off, other tools unchanged ---

    @Test
    fun `INV-TCAT-10 a tool recorded lost drops off, other tools are unchanged`() {
        val r = ToolCatalogRepository()
        r.add(drill, "Cordless drill")
        r.add(ladder, "Ladder")
        r.checkOut(l1, drill, ana, oct12)
        r.placeHold(h1, drill, ben)
        r.recordLost(drill)
        assertThat(r.rows().map { it.toolId }).containsExactly(ladder)
    }

    // --- INV-TCAT-13: by name ignoring case, then by when added ---

    @Test
    fun `INV-TCAT-13 tools are listed by name ignoring case, then by when they were added`() {
        val r = ToolCatalogRepository()
        r.add(ladder, "Ladder", at = now)
        r.add(drill, "cordless drill", at = now.plusSeconds(10))
        val second = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d4"))
        r.add(second, "Cordless Drill", at = now.plusSeconds(20))
        assertThat(r.rows().map { it.toolId }).containsExactly(drill, second, ladder)
    }

    @Test
    fun `INV-TCAT-13 tools added at the same instant keep the order they were recorded in`() {
        val r = ToolCatalogRepository()
        val first = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"))
        val second = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000e2"))
        r.add(first, "Drill", at = now)
        r.add(second, "Drill", at = now)
        assertThat(r.rows().map { it.toolId }).containsExactly(first, second)
    }

    // --- Projection threads write while REST reads: no errors, no torn or lost updates ---

    @Test
    fun `concurrent writes and reads leave a consistent final view`() {
        val r = ToolCatalogRepository()
        val tools = (1..300).map { ToolId(UUID.nameUUIDFromBytes("tool-$it".toByteArray())) }
        val writers =
            java.util.concurrent.Executors
                .newFixedThreadPool(8)
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val done = AtomicBoolean(false)

        val reader =
            Thread {
                while (!done.get()) {
                    try {
                        r.rows()
                    } catch (ex: Throwable) {
                        failures.add(ex)
                    }
                }
            }
        reader.start()

        tools.forEachIndexed { i, tool ->
            writers.submit {
                val loan = LoanId(UUID.nameUUIDFromBytes("loan-$i".toByteArray()))
                r.addTool(tool, "Tool $i", null, 7, now)
                r.checkOut(loan, tool, ana, oct12)
                r.endLoan(loan)
            }
        }
        writers.shutdown()
        assertThat(writers.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)).isTrue()
        done.set(true)
        reader.join()

        assertThat(failures).isEmpty()
        val rows = r.rows()
        assertThat(rows).hasSize(300)
        assertThat(rows.all { it.status == ToolCatalogStatus.AVAILABLE }).isTrue()
    }
}
