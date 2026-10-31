package com.toolshed.pickupstoexpire

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.retiretool.ToolRetired
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** One test per scenario in slices/pickups-to-expire.md, driving the projection and query handler directly. */
class PickupsToExpireProjectionTest {
    private val zone = ZoneId.of("America/Denver")
    private val oct13 = LocalDate.of(2026, 10, 13)
    private val now = Instant.parse("2026-10-10T16:00:00Z")

    private val drill = ToolId(uuid("drill"))
    private val ben = NeighborId(uuid("ben"))
    private val cara = NeighborId(uuid("cara"))
    private val h1 = HoldId(uuid("h1"))
    private val h2 = HoldId(uuid("h2"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private val repository = InMemoryPickupsToExpireRepository()
    private val projection = PickupsToExpireProjection(repository)

    private fun listedAt(instant: Instant): List<PickupToExpireRow> =
        ListPickupsToExpireQueryHandler(repository, Clock.fixed(instant, zone)).handle(ListPickupsToExpire)

    private fun listedOn(day: LocalDate): List<PickupToExpireRow> = listedAt(day.atStartOfDay(zone).toInstant())

    /** Reads far in the future so every row on the list counts as due: what's on the list, regardless of date. */
    private fun onList(): List<PickupToExpireRow> = listedOn(LocalDate.of(2030, 1, 1))

    private fun fulfilled(
        hold: HoldId = h1,
        neighbor: NeighborId = ben,
        collectBy: LocalDate = oct13,
    ) = HoldFulfilled(hold, drill, neighbor, collectBy, now)

    private fun checkedOut(neighbor: NeighborId) =
        ToolCheckedOut(LoanId(uuid("loan-${neighbor.value}")), drill, neighbor, 7, oct13.plusDays(7), now)

    private val h1Row = PickupToExpireRow(h1, drill, ben, oct13)

    @Test
    fun `INV-PTE-1 - given empty list, when Hold Fulfilled for H1, then H1 is on the list with collect-by 13 October, not due`() {
        assertThat(onList()).isEmpty()
        projection.on(fulfilled())
        assertThat(onList()).containsExactly(h1Row)
        assertThat(listedOn(LocalDate.of(2026, 10, 10))).isEmpty()
    }

    @Test
    fun `INV-PTE-2 - given H1 on the list, when read during 13 October shed time, then H1 is not returned`() {
        projection.on(fulfilled())
        assertThat(listedOn(oct13)).isEmpty()
        // Last instant of 13 October in Denver (MDT, UTC-6) is 05:59:59.999Z on the 14th.
        assertThat(listedAt(Instant.parse("2026-10-14T05:59:59Z"))).isEmpty()
    }

    @Test
    fun `INV-PTE-2 - given H1 on the list, when read after the start of 14 October shed time, then H1 is returned as due`() {
        projection.on(fulfilled())
        assertThat(listedAt(Instant.parse("2026-10-14T06:00:00Z"))).containsExactly(h1Row)
        assertThat(listedOn(LocalDate.of(2026, 10, 14))).containsExactly(h1Row)
    }

    @Test
    fun `INV-PTE-2 - given H1 on the list, when the day boundary crosses a daylight-saving change, then it is still midnight shed time`() {
        // Collect by 1 November 2026, the day DST ends in Denver (UTC-6 until 02:00, then UTC-7).
        projection.on(fulfilled(collectBy = LocalDate.of(2026, 11, 1)))
        assertThat(listedAt(Instant.parse("2026-11-02T06:59:59Z"))).isEmpty()
        assertThat(listedAt(Instant.parse("2026-11-02T07:00:00Z"))).hasSize(1)
    }

    @Test
    fun `INV-PTE-3 - given H1 on the list, when Ben checks the drill out, then H1 leaves the list`() {
        projection.on(fulfilled())
        projection.on(checkedOut(ben))
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-PTE-9 - given H1 due and Cara waiting, when Cara checks the drill out, then H1 stays on the list until Hold Expired`() {
        projection.on(fulfilled())
        projection.on(checkedOut(cara))
        assertThat(listedOn(LocalDate.of(2026, 10, 14))).containsExactly(h1Row)
        projection.on(HoldExpired(h1, now))
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-PTE-4 - given H1 on the list, when Hold Cancelled for H1, then H1 leaves the list`() {
        projection.on(fulfilled())
        projection.on(HoldCancelled(h1, now))
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-PTE-5 - given H1 due, when Hold Expired for H1, then H1 leaves and H2 joins with its own collect-by date`() {
        projection.on(fulfilled())
        projection.on(HoldExpired(h1, now))
        assertThat(onList()).isEmpty()
        projection.on(fulfilled(h2, cara, LocalDate.of(2026, 10, 20)))
        assertThat(onList()).containsExactly(PickupToExpireRow(h2, drill, cara, LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun `INV-PTE-6 - given H1 on the list, when the drill is retired, then H1 leaves the list`() {
        projection.on(fulfilled())
        projection.on(ToolRetired(drill, now))
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-PTE-6 - given H1 on the list, when Ben closes his account, then H1 leaves the list`() {
        projection.on(fulfilled())
        projection.on(NeighborAccountClosed(ben, false, now))
        assertThat(onList()).isEmpty()
    }

    @Test
    fun `INV-PTE-10 - given H1 on the list once, when the same Hold Fulfilled is applied again, then H1 is still there once`() {
        projection.on(fulfilled())
        projection.on(fulfilled())
        assertThat(onList()).containsExactly(h1Row)
    }

    @Test
    fun `INV-PTE-10 - given H1 has left, when its events or Hold Fulfilled are applied again, then nothing changes and nothing returns`() {
        projection.on(fulfilled())
        projection.on(HoldExpired(h1, now))
        projection.on(HoldExpired(h1, now))
        projection.on(HoldCancelled(h1, now))
        projection.on(ToolRetired(drill, now))
        projection.on(NeighborAccountClosed(ben, false, now))
        projection.on(checkedOut(ben))
        projection.on(fulfilled())
        assertThat(onList()).isEmpty()
    }
}
