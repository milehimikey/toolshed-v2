package com.toolshed.neighborstoflag

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The view's rules applied to the in-memory read model: late returns make rows, events remove them, the window is read-time. */
class NeighborsToFlagProjectionTest {
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000a"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000b"))
    private val l1 = loan(1)
    private val l2 = loan(2)
    private val l3 = loan(3)
    private val l4 = loan(4)
    private val oct5 = LocalDate.of(2026, 10, 5)

    private var clock = 0L

    private fun tick(): Instant = Instant.ofEpochSecond(1_000_000 + clock++)

    private fun loan(n: Int) = LoanId(UUID.fromString("00000000-0000-0000-0000-0000000001%02d".format(n)))

    private fun NeighborsToFlagRepository.lateReturn(
        neighbor: NeighborId,
        loanId: LoanId,
        on: LocalDate,
        late: Boolean = true,
    ) {
        checkOut(loanId, neighbor)
        returned(loanId, on, late)
    }

    private fun NeighborsToFlagRepository.listed(today: LocalDate = oct5) = neighborsToFlag(today)

    private fun threeLateReturns(): NeighborsToFlagRepository {
        val r = NeighborsToFlagRepository()
        r.lateReturn(ana, l1, LocalDate.of(2026, 9, 1))
        r.lateReturn(ana, l2, LocalDate.of(2026, 9, 20))
        r.lateReturn(ana, l3, oct5)
        return r
    }

    // --- INV-NTF-1 ---

    @Test
    fun `INV-NTF-1 - given drill loan L1 due 12 October, when returned on 12 October with late no, then no row and Ana is not listed`() {
        val r = NeighborsToFlagRepository()
        r.lateReturn(ana, l1, LocalDate.of(2026, 10, 12), late = false)
        r.lateReturn(ana, l2, LocalDate.of(2026, 9, 20), late = false)
        r.lateReturn(ana, l3, LocalDate.of(2026, 9, 21), late = false)

        assertThat(r.listed(LocalDate.of(2026, 10, 12))).isEmpty()
    }

    @Test
    fun `INV-NTF-1 - a loan still out creates no row`() {
        val r = NeighborsToFlagRepository()
        r.checkOut(l1, ana)
        r.checkOut(l2, ana)
        r.checkOut(l3, ana)

        assertThat(r.listed()).isEmpty()
    }

    // --- INV-NTF-2 ---

    @Test
    fun `INV-NTF-2 - given one late return counting, when a second late return, then Ana has two and is not listed`() {
        val r = NeighborsToFlagRepository()
        r.lateReturn(ana, l1, LocalDate.of(2026, 9, 1))
        r.lateReturn(ana, l2, LocalDate.of(2026, 9, 20))

        assertThat(r.listed()).isEmpty()
    }

    @Test
    fun `INV-NTF-2 - given late returns on 1 and 20 September, when a third on 5 October, then the list returns Ana with L1 L2 L3`() {
        val listed = threeLateReturns().listed().single()

        assertThat(listed.neighborId).isEqualTo(ana)
        assertThat(listed.loanIds).containsExactlyInAnyOrder(l1, l2, l3)
    }

    @Test
    fun `INV-NTF-2 - given late returns on 1 June and 20 September, when third on 5 October, then only two in window, not listed`() {
        val r = NeighborsToFlagRepository()
        r.lateReturn(ana, l1, LocalDate.of(2026, 6, 1))
        r.lateReturn(ana, l2, LocalDate.of(2026, 9, 20))
        r.lateReturn(ana, l3, oct5)

        assertThat(r.listed()).isEmpty()
    }

    @Test
    fun `INV-NTF-2 - given three late returns counting, when a fourth, then the list returns Ana with all four loans`() {
        val r = NeighborsToFlagRepository()
        r.lateReturn(ana, l1, LocalDate.of(2026, 9, 1))
        r.lateReturn(ana, l2, LocalDate.of(2026, 9, 10))
        r.lateReturn(ana, l3, LocalDate.of(2026, 9, 20))
        r.lateReturn(ana, l4, oct5)

        assertThat(r.listed().single().loanIds).containsExactlyInAnyOrder(l1, l2, l3, l4)
    }

    @Test
    fun `INV-NTF-2 - given returns on 8 July, 20 September and 1 October, read on 5 October (last day in window), then Ana is listed`() {
        val r = NeighborsToFlagRepository()
        r.lateReturn(ana, l1, LocalDate.of(2026, 7, 8))
        r.lateReturn(ana, l2, LocalDate.of(2026, 9, 20))
        r.lateReturn(ana, l3, LocalDate.of(2026, 10, 1))

        assertThat(r.listed(LocalDate.of(2026, 10, 5)).single().loanIds).containsExactlyInAnyOrder(l1, l2, l3)
    }

    @Test
    fun `INV-NTF-2 - same returns, when read on 6 October, the first day out of the window, then the 8 July return no longer counts`() {
        val r = NeighborsToFlagRepository()
        r.lateReturn(ana, l1, LocalDate.of(2026, 7, 8))
        r.lateReturn(ana, l2, LocalDate.of(2026, 9, 20))
        r.lateReturn(ana, l3, LocalDate.of(2026, 10, 1))

        assertThat(r.listed(LocalDate.of(2026, 10, 6))).isEmpty()
    }

    @Test
    fun `INV-NTF-2 - a return on 1 June counts through 29 August and not on 30 August`() {
        val r = NeighborsToFlagRepository()
        r.lateReturn(ana, l1, LocalDate.of(2026, 6, 1))
        r.lateReturn(ana, l2, LocalDate.of(2026, 8, 1))
        r.lateReturn(ana, l3, LocalDate.of(2026, 8, 2))

        assertThat(r.listed(LocalDate.of(2026, 8, 29))).hasSize(1)
        assertThat(r.listed(LocalDate.of(2026, 8, 30))).isEmpty()
    }

    @Test
    fun `INV-NTF-2 - each neighbor is counted separately`() {
        val r = NeighborsToFlagRepository()
        r.lateReturn(ana, l1, oct5)
        r.lateReturn(ana, l2, oct5)
        r.lateReturn(ben, l3, oct5)
        r.lateReturn(ben, l4, oct5)

        assertThat(r.listed()).isEmpty()
    }

    // --- INV-NTF-3 ---

    @Test
    fun `INV-NTF-3 - given Ana is listed with L1 L2 L3, when Neighbor Flagged names them, then rows are removed, not listed`() {
        val r = threeLateReturns()

        r.flagged(listOf(l1, l2, l3))

        assertThat(r.listed()).isEmpty()
    }

    @Test
    fun `INV-NTF-3 - given Ana was flagged using up L1 L2 L3, when L4 returned late, then one counting, not listed`() {
        val r = threeLateReturns()
        r.flagged(listOf(l1, l2, l3))

        r.lateReturn(ana, l4, LocalDate.of(2026, 10, 12))

        assertThat(r.listed(LocalDate.of(2026, 10, 12))).isEmpty()
    }

    @Test
    fun `INV-NTF-3 - a flag leaves the list needing three new late returns to flag again`() {
        val r = threeLateReturns()
        r.flagged(listOf(l1, l2, l3))
        r.lateReturn(ana, l4, LocalDate.of(2026, 10, 6))
        r.lateReturn(ana, loan(5), LocalDate.of(2026, 10, 7))
        r.lateReturn(ana, loan(6), LocalDate.of(2026, 10, 8))

        assertThat(r.listed(LocalDate.of(2026, 10, 8)).single().loanIds).containsExactlyInAnyOrder(l4, loan(5), loan(6))
    }

    @Test
    fun `INV-NTF-3 - a replayed Tool Returned does not put a used-up row back`() {
        val r = threeLateReturns()
        r.flagged(listOf(l1, l2, l3))

        r.returned(l1, LocalDate.of(2026, 9, 1), true)

        assertThat(r.listed()).isEmpty()
        r.lateReturn(ana, l4, oct5)
        r.lateReturn(ana, loan(5), oct5)
        assertThat(r.listed()).isEmpty()
    }

    // --- INV-NTF-5 ---

    @Test
    fun `INV-NTF-5 - given Ana was suspended and reinstated with two before, when a late return, then one counting, not listed`() {
        val r = NeighborsToFlagRepository()
        r.lateReturn(ana, l1, LocalDate.of(2026, 9, 1))
        r.lateReturn(ana, l2, LocalDate.of(2026, 9, 20))
        r.suspend(ana, tick())
        r.reinstate(ana, tick())

        r.lateReturn(ana, l3, LocalDate.of(2026, 10, 10))

        assertThat(r.listed(LocalDate.of(2026, 10, 10))).isEmpty()
    }

    @Test
    fun `INV-NTF-5 - after reinstatement a new late return counts again`() {
        val r = NeighborsToFlagRepository()
        r.suspend(ana, tick())
        r.reinstate(ana, tick())
        r.lateReturn(ana, l1, oct5)
        r.lateReturn(ana, l2, oct5)
        r.lateReturn(ana, l3, oct5)

        assertThat(r.listed().single().neighborId).isEqualTo(ana)
    }

    // --- INV-NTF-6 ---

    @Test
    fun `INV-NTF-6 - given Ana is listed, when Neighbor Account Closed, then her rows are removed and she is not listed`() {
        val r = threeLateReturns()

        r.close(ana)

        assertThat(r.listed()).isEmpty()
    }

    @Test
    fun `INV-NTF-6 - a late return for a closed account creates no row`() {
        val r = NeighborsToFlagRepository()
        r.checkOut(l1, ana)
        r.close(ana)

        r.returned(l1, oct5, true)
        r.lateReturn(ana, l2, oct5)
        r.lateReturn(ana, l3, oct5)

        assertThat(r.listed()).isEmpty()
    }

    @Test
    fun `INV-NTF-6 - a closed account stays out of the list after a reinstatement event`() {
        val r = NeighborsToFlagRepository()
        r.close(ana)
        r.reinstate(ana, tick())
        r.lateReturn(ana, l1, oct5)
        r.lateReturn(ana, l2, oct5)
        r.lateReturn(ana, l3, oct5)

        assertThat(r.listed()).isEmpty()
    }

    // --- INV-NTF-7 ---

    @Test
    fun `INV-NTF-7 - given Ana is listed, when Neighbor Suspended, then her rows are removed and she is not listed`() {
        val r = threeLateReturns()

        r.suspend(ana, tick())

        assertThat(r.listed()).isEmpty()
    }

    @Test
    fun `INV-NTF-7 - given Ana is suspended, when her loan is returned late, then no row is created`() {
        val r = NeighborsToFlagRepository()
        r.checkOut(l1, ana)
        r.checkOut(l2, ana)
        r.checkOut(l3, ana)
        r.suspend(ana, tick())

        r.returned(l1, oct5, true)
        r.returned(l2, oct5, true)
        r.returned(l3, oct5, true)
        r.reinstate(ana, tick())

        assertThat(r.listed()).isEmpty()
    }

    // --- INV-NTF-8 ---

    @Test
    fun `INV-NTF-8 - given late returns on 1 and 20 September, when the same Tool Returned is applied again, then Ana still has two`() {
        val r = NeighborsToFlagRepository()
        r.lateReturn(ana, l1, LocalDate.of(2026, 9, 1))
        r.lateReturn(ana, l2, LocalDate.of(2026, 9, 20))

        r.checkOut(l2, ana)
        r.returned(l2, LocalDate.of(2026, 9, 20), true)

        assertThat(r.listed()).isEmpty()
        r.lateReturn(ana, l3, oct5)
        assertThat(r.listed().single().loanIds).containsExactlyInAnyOrder(l1, l2, l3)
    }

    @Test
    fun `INV-NTF-8 - applying flagged and close twice leaves the list as after once`() {
        val r = threeLateReturns()
        r.flagged(listOf(l1, l2, l3))
        r.flagged(listOf(l1, l2, l3))
        r.lateReturn(ben, l4, oct5)
        r.close(ben)
        r.close(ben)

        assertThat(r.listed()).isEmpty()
    }

    @Test
    fun `INV-NTF-8 - given Ana reinstated then L3 returned late, when the reinstatement is delivered again, then L3 still counts`() {
        val r = NeighborsToFlagRepository()
        val reinstatedAt = tick()
        r.lateReturn(ana, l1, LocalDate.of(2026, 9, 1))
        r.reinstate(ana, reinstatedAt)
        r.lateReturn(ana, l2, LocalDate.of(2026, 9, 20))
        r.lateReturn(ana, l3, oct5)

        // redelivered batch [Reinstated, ToolReturned L3]
        r.reinstate(ana, reinstatedAt)
        r.returned(l3, oct5, true)
        r.checkOut(l3, ana)

        assertThat(r.listed()).isEmpty()
        r.lateReturn(ana, l4, oct5)
        assertThat(r.listed().single().loanIds).containsExactlyInAnyOrder(l2, l3, l4)
    }

    @Test
    fun `INV-NTF-8 - given reinstated then L3 late, when suspension is redelivered, then Ana is not suspended and L3 counts`() {
        val r = NeighborsToFlagRepository()
        val suspendedAt = tick()
        r.suspend(ana, suspendedAt)
        r.reinstate(ana, tick())
        r.lateReturn(ana, l3, oct5)

        r.suspend(ana, suspendedAt)
        r.lateReturn(ana, l4, oct5)
        r.lateReturn(ana, l1, oct5)

        assertThat(r.listed().single().loanIds).containsExactlyInAnyOrder(l1, l3, l4)
    }
}
