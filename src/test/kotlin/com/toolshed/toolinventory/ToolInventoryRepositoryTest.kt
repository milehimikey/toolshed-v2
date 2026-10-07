package com.toolshed.toolinventory

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The view's rules, applied to the in-memory read model: one row per added tool, retirement, loans, order, overdue. */
class ToolInventoryRepositoryTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
    private val ladder = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d2"))
    private val trimmer = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d3"))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val l1 = LoanId(UUID.fromString("00000000-0000-0000-0000-000000000011"))
    private val oct12 = LocalDate.of(2026, 10, 12)

    private fun ToolInventoryRepository.add(
        tool: ToolId,
        name: String,
        description: String? = null,
        days: Int = 7,
        at: Instant = now,
    ) = addTool(tool, name, description, days, at)

    // --- INV-TINV-1 and INV-TINV-8: one entry per added tool; a replay changes nothing ---

    @Test
    fun `INV-TINV-1 two identical drills are two entries`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        r.add(ladder, "Cordless drill")
        assertThat(r.rows(oct12)).hasSize(2)
    }

    @Test
    fun `INV-TINV-8 applying the same Tool Added twice leaves one entry`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        r.add(drill, "Cordless drill")
        assertThat(r.rows(oct12)).hasSize(1)
    }

    // --- INV-TINV-4: name, description when present, and the default period ---

    @Test
    fun `INV-TINV-4 a tool with a description shows it, and one without shows none`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill", description = "18V, two batteries", days = 7)
        r.add(ladder, "Ladder", description = null, days = 3)
        val rows = r.rows(oct12).associateBy { it.toolId }
        assertThat(rows.getValue(drill).description).isEqualTo("18V, two batteries")
        assertThat(rows.getValue(drill).defaultCheckoutPeriodDays).isEqualTo(7)
        assertThat(rows.getValue(ladder).description).isNull()
        assertThat(rows.getValue(ladder).defaultCheckoutPeriodDays).isEqualTo(3)
    }

    // --- INV-TINV-5: a tool out shows the borrower's name and the due date; they go when it comes back ---

    @Test
    fun `INV-TINV-5 a tool out shows the borrower's registered name and due date, and drops them when returned`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        r.registerNeighbor(ana, "Ana Ruiz")
        r.checkOut(l1, drill, ana, oct12)
        val out = r.rows(oct12).single()
        assertThat(out.onLoan).isTrue()
        assertThat(out.borrowerName).isEqualTo("Ana Ruiz")
        assertThat(out.dueDate).isEqualTo(oct12)

        r.endLoan(l1)
        val back = r.rows(oct12).single()
        assertThat(back.onLoan).isFalse()
        assertThat(back.borrowerName).isNull()
        assertThat(back.dueDate).isNull()
    }

    @Test
    fun `INV-TINV-5 the due date moves with each extension`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        r.extend(l1, LocalDate.of(2026, 10, 19))
        assertThat(r.rows(oct12).single().dueDate).isEqualTo(LocalDate.of(2026, 10, 19))
    }

    // --- INV-TINV-6 and INV-TINV-7: mark lost only when out; retire only when not already retired ---

    @Test
    fun `INV-TINV-6 mark lost is offered only while the tool is out`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        assertThat(r.rows(oct12).single().canMarkLost).isFalse()
        r.checkOut(l1, drill, ana, oct12)
        assertThat(r.rows(oct12).single().canMarkLost).isTrue()
    }

    @Test
    fun `INV-TINV-7 retire is offered on every listed tool that is not already retired`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        assertThat(r.rows(oct12).single().canRetire).isTrue()
        r.retire(drill)
        r.checkOut(l1, drill, ana, oct12)
        val stillListed = r.rows(oct12).single()
        assertThat(stillListed.retired).isTrue()
        assertThat(stillListed.canRetire).isFalse()
        assertThat(stillListed.canMarkLost).isTrue()
    }

    // --- INV-TINV-2: a retired tool drops off once back in the shed or recorded lost ---

    @Test
    fun `INV-TINV-2 a tool retired in the shed drops off at once, and the other tool stays`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        r.add(ladder, "Ladder")
        r.retire(drill)
        assertThat(r.rows(oct12).map { it.toolId }).containsExactly(ladder)
    }

    @Test
    fun `INV-TINV-2 a tool retired while out stays listed until returned, then drops off`() {
        val r = ToolInventoryRepository()
        r.add(ladder, "Ladder")
        r.checkOut(l1, ladder, ana, oct12)
        r.retire(ladder)
        assertThat(r.rows(oct12).single().retired).isTrue()
        r.endLoan(l1)
        assertThat(r.rows(oct12)).isEmpty()
    }

    @Test
    fun `INV-TINV-2 a tool recorded lost is retired by that and drops off`() {
        val r = ToolInventoryRepository()
        r.add(trimmer, "Hedge trimmer")
        r.checkOut(l1, trimmer, ana, oct12)
        r.endLoan(l1)
        r.retire(trimmer)
        assertThat(r.rows(oct12)).isEmpty()
    }

    // --- INV-TINV-9: overdue is worked out against today, in the shed's zone ---

    @Test
    fun `INV-TINV-9 a tool out is overdue only after its due date`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        r.checkOut(l1, drill, ana, oct12)
        assertThat(r.rows(oct12).single().overdue).isFalse()
        assertThat(r.rows(LocalDate.of(2026, 10, 13)).single().overdue).isTrue()
    }

    // --- INV-TINV-10: by name ignoring case, then when added, then record order ---

    @Test
    fun `INV-TINV-10 tools are listed by name ignoring case, then by when they were added`() {
        val r = ToolInventoryRepository()
        r.add(ladder, "Ladder", at = now)
        r.add(drill, "cordless drill", at = now.plusSeconds(10))
        val second = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000d4"))
        r.add(second, "Cordless Drill", at = now.plusSeconds(20))
        assertThat(r.rows(oct12).map { it.toolId }).containsExactly(drill, second, ladder)
    }

    @Test
    fun `INV-TINV-10 tools added at the same instant keep the order they were recorded in`() {
        val r = ToolInventoryRepository()
        val first = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"))
        val second = ToolId(UUID.fromString("00000000-0000-0000-0000-0000000000e2"))
        r.add(first, "Drill", at = now)
        r.add(second, "Drill", at = now)
        assertThat(r.rows(oct12).map { it.toolId }).containsExactly(first, second)
    }

    // --- Projection threads write while REST reads: no errors, no lost updates ---

    /** The view-concurrency ruling: projection threads write while readers query; no error, and the final view is right. */
    @Test
    fun `concurrent writes and reads leave a consistent final view`() {
        val r = ToolInventoryRepository()
        val tools = (1..300).map { ToolId(UUID.nameUUIDFromBytes("tool-$it".toByteArray())) }
        val borrower = ana
        val writers =
            java.util.concurrent.Executors
                .newFixedThreadPool(8)
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val done =
            java.util.concurrent.atomic
                .AtomicBoolean(false)

        val reader =
            Thread {
                while (!done.get()) {
                    try {
                        r.rows(oct12)
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
                r.checkOut(loan, tool, borrower, oct12)
                r.endLoan(loan)
            }
        }
        writers.shutdown()
        assertThat(writers.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)).isTrue()
        done.set(true)
        reader.join()

        assertThat(failures).isEmpty()
        val rows = r.rows(oct12)
        assertThat(rows).hasSize(300)
        assertThat(rows.none { it.onLoan }).isTrue()
    }

    // --- INV-TINV-3: a neighbor registering changes no entry ---

    @Test
    fun `INV-TINV-3 a neighbor registering leaves the entries of tools in the shed unchanged`() {
        val r = ToolInventoryRepository()
        r.add(drill, "Cordless drill")
        val before = r.rows(oct12)
        r.registerNeighbor(NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b9")), "Ben Cole")
        assertThat(r.rows(oct12)).isEqualTo(before)
    }

    // --- INV-TINV-2: a tool retired while out, then recorded lost, drops off ---

    @Test
    fun `INV-TINV-2 a tool retired while out and then recorded lost drops off`() {
        val r = ToolInventoryRepository()
        r.add(trimmer, "Hedge trimmer")
        r.checkOut(l1, trimmer, ana, oct12)
        r.retire(trimmer)
        assertThat(r.rows(oct12).single().retired).isTrue()
        // Tool Lost ends the loan and retires the tool.
        r.endLoan(l1)
        r.retire(trimmer)
        assertThat(r.rows(oct12)).isEmpty()
    }
}
