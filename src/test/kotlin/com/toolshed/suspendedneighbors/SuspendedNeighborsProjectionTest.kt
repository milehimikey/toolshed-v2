package com.toolshed.suspendedneighbors

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.NeighborId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import com.toolshed.web.Caller
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * One test per scenario in slices/suspended-neighbors.md, driving [SuspendedNeighborsProjection] and
 * [ListSuspendedNeighborsQueryHandler] directly against a fresh [InMemorySuspendedNeighborsRepository].
 */
class SuspendedNeighborsProjectionTest {
    private val registeredAt = Instant.parse("2026-08-01T04:00:00Z")

    private val ana = NeighborId(uuid("n1"))
    private val ben = NeighborId(uuid("n2"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private fun on(
        month: Int,
        day: Int,
        year: Int = 2026,
    ): Instant = Instant.parse("%d-%02d-%02dT15:00:00Z".format(year, month, day))

    private class Harness {
        val repository = InMemorySuspendedNeighborsRepository()
        val projection = SuspendedNeighborsProjection(repository)
        val queryHandler = ListSuspendedNeighborsQueryHandler(repository)

        fun apply(vararg events: Any) {
            events.forEach { event ->
                when (event) {
                    is NeighborRegistered -> projection.on(event)
                    is NeighborEmailChanged -> projection.on(event)
                    is NeighborSuspended -> projection.on(event)
                    is NeighborReinstated -> projection.on(event)
                    is NeighborAccountClosed -> projection.on(event)
                    else -> error("unexpected event ${event::class.simpleName}")
                }
            }
        }

        fun list(): List<SuspendedNeighborRow> = queryHandler.handle(ListSuspendedNeighbors).entries
    }

    private fun background(): Harness =
        Harness().also {
            it.apply(
                NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", registeredAt),
                NeighborRegistered(ben, "Ben Ito", "ben@example.org", registeredAt),
            )
        }

    // --- INV-SPN-1, INV-SPN-2, INV-SPN-4 ---

    @Test
    fun `INV-SPN-1 INV-SPN-2 INV-SPN-4 given empty view, when Ana suspended 7 October, then lists Ana with Reinstate offered`() {
        val h = background()
        assertThat(h.list()).isEmpty()

        h.apply(NeighborSuspended(ana, on(10, 7)))

        // Reinstate is offered on every entry (INV-SPN-4): each row is an entry the screen can reinstate by neighborId.
        assertThat(h.list()).containsExactly(SuspendedNeighborRow(ana, "Ana Ruiz", "ana@example.org", on(10, 7)))
    }

    // --- INV-SPN-2 ---

    @Test
    fun `INV-SPN-2 given Ana changed her email before suspension, when Neighbor Suspended, then lists the new email`() {
        val h = background()
        h.apply(NeighborEmailChanged(ana, "ana.ruiz@example.net", on(9, 1)))

        h.apply(NeighborSuspended(ana, on(10, 7)))

        val row = h.list().single()
        assertThat(row.name).isEqualTo("Ana Ruiz")
        assertThat(row.email).isEqualTo("ana.ruiz@example.net")
    }

    // --- INV-SPN-3 ---

    @Test
    fun `INV-SPN-3 given Ana suspended, when unrelated events arrive over time, then Ana is still listed`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)))

        h.apply(
            NeighborEmailChanged(ben, "ben2@example.org", on(12, 1)),
            NeighborSuspended(ben, on(12, 2)),
            NeighborReinstated(ben, on(12, 3)),
        )

        assertThat(h.list().map { it.neighborId }).containsExactly(ana)
    }

    // --- INV-SPN-4 ---

    @Test
    fun `INV-SPN-4 given Ana and Ben listed, when Neighbor Reinstated for Ana, then Ana leaves and Ben is still listed`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborSuspended(ben, on(10, 8)))

        h.apply(NeighborReinstated(ana, on(10, 9)))

        assertThat(h.list().map { it.neighborId }).containsExactly(ben)
    }

    // --- INV-SPN-5 ---

    @Test
    fun `INV-SPN-5 given Ana listed, when Neighbor Account Closed for Ana, then Ana leaves`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)))

        h.apply(NeighborAccountClosed(ana, true, on(10, 8)))

        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-SPN-5 given an entry gone, when a later reinstatement or closure arrives, then nothing changes, new suspension starts anew`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborReinstated(ana, on(10, 8)))

        h.apply(NeighborReinstated(ana, on(10, 9)), NeighborAccountClosed(ana, false, on(10, 10)))
        assertThat(h.list()).isEmpty()

        h.apply(NeighborSuspended(ana, on(11, 1)))
        assertThat(h.list().single().suspendedAt).isEqualTo(on(11, 1))
    }

    // --- INV-SPN-6 ---

    @Test
    fun `INV-SPN-6 given Ana reinstated 1 November, when suspended again 3 February, then listed once suspended 3 February`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborReinstated(ana, on(11, 1)))

        h.apply(NeighborSuspended(ana, on(2, 3, 2027)))

        val row = h.list().single()
        assertThat(row.neighborId).isEqualTo(ana)
        assertThat(row.suspendedAt).isEqualTo(on(2, 3, 2027))
    }

    // --- INV-SPN-9 ---

    @Test
    fun `INV-SPN-9 given Ana listed once, when the same suspension is applied again, then Ana is listed once suspended 7 October`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)))

        h.apply(NeighborSuspended(ana, on(10, 7)))

        val row = h.list().single()
        assertThat(row.suspendedAt).isEqualTo(on(10, 7))
    }

    @Test
    fun `INV-SPN-9 given a replayed suspension after reinstatement, when applied again, then Ana does not come back`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborReinstated(ana, on(10, 8)))

        h.apply(NeighborSuspended(ana, on(10, 7)))

        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-SPN-9 given replayed Registered and Email Changed, when applied again, then the email is not reset`() {
        val h = background()
        h.apply(NeighborEmailChanged(ana, "ana.ruiz@example.net", on(9, 1)))
        h.apply(
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", registeredAt),
            NeighborEmailChanged(ana, "ana.ruiz@example.net", on(9, 1)),
        )

        h.apply(NeighborSuspended(ana, on(10, 7)))

        assertThat(h.list().single().email).isEqualTo("ana.ruiz@example.net")
    }

    // --- INV-SPN-10 ---

    @Test
    fun `INV-SPN-10 given Ben suspended 1 October and Ana 7 October, when listed, then Ana Ruiz comes first then Ben Ito`() {
        val h = background()
        h.apply(NeighborSuspended(ben, on(10, 1)), NeighborSuspended(ana, on(10, 7)))

        assertThat(h.list().map { it.name }).containsExactly("Ana Ruiz", "Ben Ito")
    }

    @Test
    fun `INV-SPN-10 given two neighbors with the same name ignoring case, when listed, then the earlier suspension comes first`() {
        val h = background()
        val other = NeighborId(uuid("n9"))
        h.apply(NeighborRegistered(other, "ana ruiz", "other@example.org", registeredAt))
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborSuspended(other, on(10, 1)))

        assertThat(h.list().map { it.neighborId }).containsExactly(other, ana)
    }

    // --- Listing suspended neighbors ---

    @Test
    fun `given Ana and Ben listed, when the screen lists suspended neighbors, then both come back with all fields`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborSuspended(ben, on(10, 8)))

        val rows = h.list()

        assertThat(rows).containsExactly(
            SuspendedNeighborRow(ana, "Ana Ruiz", "ana@example.org", on(10, 7)),
            SuspendedNeighborRow(ben, "Ben Ito", "ben@example.org", on(10, 8)),
        )
    }

    // --- INV-SPN-11 ---

    @Test
    fun `INV-SPN-11 given entries, when a neighbor without the shed-manager role asks, then refused not-shed-manager`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller(neighborId = ben, isShedManager = false)) { requireSuspendedNeighborsReader() }
            }
        assertThat(refused.reason).isEqualTo("not-shed-manager")
        assertThat(refused.message).isEqualTo("Only the shed manager can see this screen.")
    }

    @Test
    fun `INV-SPN-11 given entries, when a caller with no sign-in asks, then refused not-signed-in`() {
        val refused = assertThrows<CommandRejected> { with(Caller.ANONYMOUS) { requireSuspendedNeighborsReader() } }
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }

    @Test
    fun `INV-SPN-11 given the shed manager, when asking, then allowed`() {
        with(Caller(neighborId = null, isShedManager = true)) { requireSuspendedNeighborsReader() }
    }
}
