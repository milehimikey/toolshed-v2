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
    private val registered = Instant.parse("2026-09-01T15:00:00Z")
    private val ana = NeighborId(UUID.nameUUIDFromBytes("n1".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("n2".toByteArray()))

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
                NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", registered),
                NeighborRegistered(ben, "Ben Ito", "ben@example.org", registered),
            )
        }

    // --- INV-SPN-1, INV-SPN-2, INV-SPN-4 ---

    @Test
    fun `INV-SPN-1 INV-SPN-2 INV-SPN-4 given empty list, when Ana suspended 7 October, then lists Ana with name email date`() {
        val h = background()
        assertThat(h.list()).isEmpty()
        h.apply(NeighborSuspended(ana, on(10, 7)))
        assertThat(h.list()).containsExactly(SuspendedNeighborRow(ana, "Ana Ruiz", "ana@example.org", on(10, 7)))
    }

    @Test
    fun `INV-SPN-2 given Ana changed her email before suspension, when suspended, then lists the new email`() {
        val h = background()
        h.apply(
            NeighborEmailChanged(ana, "ana.ruiz@example.net", on(9, 15)),
            NeighborSuspended(ana, on(10, 7)),
        )
        assertThat(h.list().single().email).isEqualTo("ana.ruiz@example.net")
        assertThat(h.list().single().name).isEqualTo("Ana Ruiz")
    }

    @Test
    fun `INV-SPN-2 given a repeated Neighbor Registered after an email change, then the current email stays`() {
        val h = background()
        h.apply(
            NeighborEmailChanged(ana, "ana.ruiz@example.net", on(9, 15)),
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", registered),
            NeighborSuspended(ana, on(10, 7)),
        )
        assertThat(h.list().single().email).isEqualTo("ana.ruiz@example.net")
    }

    // --- INV-SPN-3, INV-SPN-4 ---

    @Test
    fun `INV-SPN-3 INV-SPN-4 given Ana and Ben listed, when Ana is reinstated, then Ana leaves and Ben stays`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborSuspended(ben, on(10, 8)))
        h.apply(NeighborReinstated(ana, on(11, 1)))
        assertThat(h.list().map { it.neighborId }).containsExactly(ben)
    }

    // --- INV-SPN-5 ---

    @Test
    fun `INV-SPN-5 given Ana listed, when Ana closes her account, then Ana leaves the list`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)))
        h.apply(NeighborAccountClosed(ana, true, on(10, 20)))
        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-SPN-5 given no entry, when a later reinstatement or closure arrives, then nothing changes`() {
        val h = background()
        h.apply(NeighborSuspended(ben, on(10, 8)))
        h.apply(NeighborReinstated(ana, on(11, 1)), NeighborAccountClosed(ana, false, on(11, 2)))
        assertThat(h.list().map { it.neighborId }).containsExactly(ben)
    }

    // --- INV-SPN-6 ---

    @Test
    fun `INV-SPN-6 given Ana suspended then reinstated, when suspended again on 3 February, then listed once with the new date`() {
        val h = background()
        h.apply(
            NeighborSuspended(ana, on(10, 7)),
            NeighborReinstated(ana, on(11, 1)),
            NeighborSuspended(ana, on(2, 3, 2027)),
        )
        assertThat(h.list().map { it.neighborId to it.suspendedAt }).containsExactly(ana to on(2, 3, 2027))
    }

    @Test
    fun `INV-SPN-6 given Ana listed, when suspended again without a reinstatement, then still listed once`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborSuspended(ana, on(10, 9)))
        assertThat(h.list()).hasSize(1)
    }

    // --- INV-SPN-9 ---

    @Test
    fun `INV-SPN-9 given Ana listed once, when the same Neighbor Suspended is applied again, then still once, suspended 7 October`() {
        val h = background()
        val suspended = NeighborSuspended(ana, on(10, 7))
        h.apply(suspended, suspended)
        assertThat(h.list().map { it.neighborId to it.suspendedAt }).containsExactly(ana to on(10, 7))
    }

    @Test
    fun `INV-SPN-9 given Ana reinstated, when the old Neighbor Suspended is replayed, then Ana is not listed again`() {
        val h = background()
        val suspended = NeighborSuspended(ana, on(10, 7))
        h.apply(suspended, NeighborReinstated(ana, on(11, 1)), suspended)
        assertThat(h.list()).isEmpty()
    }

    @Test
    fun `INV-SPN-9 given a reinstatement applied twice, then Ben is untouched`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborSuspended(ben, on(10, 8)))
        val reinstated = NeighborReinstated(ana, on(11, 1))
        h.apply(reinstated, reinstated)
        assertThat(h.list().map { it.neighborId }).containsExactly(ben)
    }

    // --- INV-SPN-10 ---

    @Test
    fun `INV-SPN-10 given Ben suspended 1 October and Ana 7 October, when listed, then Ana first then Ben`() {
        val h = background()
        h.apply(NeighborSuspended(ben, on(10, 1)), NeighborSuspended(ana, on(10, 7)))
        assertThat(h.list().map { it.name }).containsExactly("Ana Ruiz", "Ben Ito")
    }

    @Test
    fun `INV-SPN-10 given names differing in case, when listed, then ordered ignoring case, then by suspendedAt`() {
        val h = background()
        val lower = NeighborId(UUID.nameUUIDFromBytes("n3".toByteArray()))
        val twin = NeighborId(UUID.nameUUIDFromBytes("n4".toByteArray()))
        h.apply(
            NeighborRegistered(lower, "ana ruiz", "lower@example.org", registered),
            NeighborRegistered(twin, "Ana Ruiz", "twin@example.org", registered),
            NeighborSuspended(ben, on(10, 1)),
            NeighborSuspended(twin, on(10, 9)),
            NeighborSuspended(lower, on(10, 5)),
        )
        assertThat(h.list().map { it.neighborId }).containsExactly(lower, twin, ben)
    }

    // --- Listing ---

    @Test
    fun `given Ana and Ben listed, when listed, then both entries are returned with all fields`() {
        val h = background()
        h.apply(NeighborSuspended(ana, on(10, 7)), NeighborSuspended(ben, on(10, 8)))
        assertThat(h.list())
            .containsExactly(
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
