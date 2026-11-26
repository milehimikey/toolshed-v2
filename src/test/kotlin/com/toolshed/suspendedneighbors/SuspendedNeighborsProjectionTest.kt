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
 * Together these cover every INV-SPN id in the doc.
 */
class SuspendedNeighborsProjectionTest {
    private val now = Instant.parse("2026-09-01T04:00:00Z")

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

    /** Ana Ruiz registered with ana@example.org, and Ben Ito with ben@example.org. */
    private fun background(): Harness {
        val h = Harness()
        h.apply(
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            NeighborRegistered(ben, "Ben Ito", "ben@example.org", now),
        )
        return h
    }

    // --- INV-SPN-1, INV-SPN-2, INV-SPN-4 ---

    @Test
    fun `INV-SPN-1 INV-SPN-2 INV-SPN-4 given empty view, when Ana suspended on 7 October, then lists Ana with her email and date`() {
        val h = background()
        assertThat(h.list()).isEmpty()

        h.apply(NeighborSuspended(ana, on(10, 7)))

        assertThat(h.list()).containsExactly(SuspendedNeighborRow(ana, "Ana Ruiz", "ana@example.org", on(10, 7)))
    }

    // --- INV-SPN-2 ---

    @Test
    fun `INV-SPN-2 given Ana changed her email before suspension, when suspended, then lists the new email`() {
        val h = background()
        h.apply(
            NeighborEmailChanged(ana, "ana.ruiz@example.net", on(9, 20)),
            NeighborSuspended(ana, on(10, 7)),
        )

        val row = h.list().single()
        assertThat(row.name).isEqualTo("Ana Ruiz")
        assertThat(row.email).isEqualTo("ana.ruiz@example.net")
    }

    // --- INV-SPN-3, INV-SPN-4 ---

    @Test
    fun `INV-SPN-3 INV-SPN-4 given Ana and Ben listed, when Ana reinstated, then Ana leaves and Ben stays`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborSuspended(ben, on(10, 8)))
        assertThat(h.list().map { it.neighborId }).containsExactly(ana, ben)

        h.apply(NeighborReinstated(ana, on(11, 1)))

        assertThat(h.list().map { it.neighborId }).containsExactly(ben)
    }

    @Test
    fun `INV-SPN-3 given a long-standing suspension and no other event, then the entry stays listed`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(1, 7, 2026)))

        assertThat(h.list().map { it.neighborId }).containsExactly(ana)
    }

    // --- INV-SPN-5 ---

    @Test
    fun `INV-SPN-5 given Ana listed, when Ana closes her account, then Ana leaves`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)))

        h.apply(NeighborAccountClosed(ana, true, on(10, 20)))

        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-SPN-5 given an entry already gone, when reinstated or closed later, then nothing changes`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborSuspended(ben, on(10, 8)))
        h.apply(NeighborReinstated(ana, on(11, 1)))

        h.apply(NeighborReinstated(ana, on(11, 2)), NeighborAccountClosed(ana, false, on(11, 3)))

        assertThat(h.list().map { it.neighborId }).containsExactly(ben)
    }

    // --- INV-SPN-6 ---

    @Test
    fun `INV-SPN-6 given Ana suspended 7 October and reinstated, when suspended again 3 February, then listed once with the new date`() {
        val h = background()
        h.apply(
            NeighborSuspended(ana, on(10, 7)),
            NeighborReinstated(ana, on(11, 1)),
            NeighborSuspended(ana, on(2, 3, 2027)),
        )

        assertThat(h.list().map { it.neighborId to it.suspendedAt }).containsExactly(ana to on(2, 3, 2027))
    }

    // --- INV-SPN-9 ---

    @Test
    fun `INV-SPN-9 given Ana listed once, when the same Neighbor Suspended is applied again, then still once with the same date`() {
        val h = background()
        val suspended = NeighborSuspended(ana, on(10, 7))
        h.apply(suspended)

        h.apply(suspended)

        assertThat(h.list().map { it.neighborId to it.suspendedAt }).containsExactly(ana to on(10, 7))
    }

    @Test
    fun `INV-SPN-9 INV-SPN-3 given Ana suspended again, when the tail is replayed, then Ana stays listed with the new date`() {
        val h = background()
        val tail = arrayOf(NeighborReinstated(ana, on(11, 1)), NeighborSuspended(ana, on(2, 3, 2027)))
        h.apply(NeighborSuspended(ana, on(10, 7)), *tail)

        h.apply(*tail)

        assertThat(h.list().map { it.neighborId to it.suspendedAt }).containsExactly(ana to on(2, 3, 2027))
    }

    @Test
    fun `INV-SPN-9 INV-SPN-3 given Ana suspended again, when the earlier Account Closed is replayed, then Ana stays listed`() {
        val h = background()
        h.apply(
            NeighborSuspended(ana, on(10, 7)),
            NeighborAccountClosed(ana, true, on(10, 20)),
            NeighborSuspended(ana, on(2, 3, 2027)),
        )

        h.apply(NeighborAccountClosed(ana, true, on(10, 20)), NeighborSuspended(ana, on(2, 3, 2027)))

        assertThat(h.list().map { it.neighborId to it.suspendedAt }).containsExactly(ana to on(2, 3, 2027))
    }

    @Test
    fun `INV-SPN-9 given a repeated registration after an email change, then the current email is kept`() {
        val h = Harness()
        val registered = NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now)
        h.apply(
            registered,
            NeighborEmailChanged(ana, "ana.ruiz@example.net", on(9, 20)),
            NeighborSuspended(ana, on(10, 7)),
        )

        h.apply(registered, NeighborEmailChanged(ana, "ana.ruiz@example.net", on(9, 20)))

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
    fun `INV-SPN-10 given two neighbors with the same name ignoring case, when listed, then ordered by suspension time`() {
        val h = background()
        val other = NeighborId(uuid("n3"))
        h.apply(
            NeighborRegistered(other, "ana ruiz", "other@example.org", now),
            NeighborSuspended(other, on(10, 9)),
            NeighborSuspended(ana, on(10, 7)),
        )

        assertThat(h.list().map { it.neighborId }).containsExactly(ana, other)
    }

    // --- Listing ---

    @Test
    fun `given Ana and Ben listed, when the screen lists suspended neighbors, then both entries come back with all fields`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborSuspended(ben, on(10, 1)))

        assertThat(h.list()).containsExactly(
            SuspendedNeighborRow(ana, "Ana Ruiz", "ana@example.org", on(10, 7)),
            SuspendedNeighborRow(ben, "Ben Ito", "ben@example.org", on(10, 1)),
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
