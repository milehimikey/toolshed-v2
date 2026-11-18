package com.toolshed.neighbors

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
 * One test per scenario in slices/neighbors.md, driving [NeighborsProjection] and [ListNeighborsQueryHandler]
 * directly against a fresh [InMemoryNeighborsRepository]. Together these cover every INV-NBRS id in the doc.
 */
class NeighborsProjectionTest {
    private val ana = NeighborId(uuid("n1"))
    private val ana2 = NeighborId(uuid("n2"))
    private val ben = NeighborId(uuid("n3"))
    private val cara = NeighborId(uuid("n4"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private fun at(
        month: Int,
        day: Int,
    ): Instant = Instant.parse("2026-%02d-%02dT15:00:00Z".format(month, day))

    private val now = at(10, 5)

    private class Harness {
        val repository = InMemoryNeighborsRepository()
        val projection = NeighborsProjection(repository)
        val queryHandler = ListNeighborsQueryHandler(repository)

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

        fun list(): List<NeighborRow> = queryHandler.handle(ListNeighbors).entries
    }

    private fun registered(
        id: NeighborId,
        name: String,
        email: String,
        registeredAt: Instant,
    ) = NeighborRegistered(id, name, email, registeredAt)

    private fun closed(id: NeighborId) = NeighborAccountClosed(id, false, now)

    private fun closedWhileSuspended(id: NeighborId) = NeighborAccountClosed(id, true, now)

    // --- INV-NBRS-1, INV-NBRS-2, INV-NBRS-5, INV-NBRS-6 ---
    @Test
    fun `INV-NBRS-1 INV-NBRS-2 INV-NBRS-5 INV-NBRS-6 given no neighbors, when Ana registers, then listed active with Suspend offered`() {
        val h = Harness()
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org", now))
        val row = h.list().single()
        assertThat(row.neighborId).isEqualTo(ana)
        assertThat(row.name).isEqualTo("Ana Ruiz")
        assertThat(row.email).isEqualTo("ana@example.org")
        assertThat(row.registeredAt).isEqualTo(now)
        assertThat(row.status).isEqualTo(NeighborStatus.ACTIVE)
        assertThat(row.canSuspend).isTrue()
    }

    // --- INV-NBRS-1 ---
    @Test
    fun `INV-NBRS-1 given Ana listed, when a different account also named Ana Ruiz registers, then both are listed`() {
        val h = Harness()
        h.apply(
            registered(ana, "Ana Ruiz", "ana@example.org", now),
            registered(ana2, "Ana Ruiz", "ana.r@example.net", now),
        )
        assertThat(h.list().map { it.neighborId }).containsExactlyInAnyOrder(ana, ana2)
        assertThat(h.list().map { it.email }).containsExactlyInAnyOrder("ana@example.org", "ana.r@example.net")
    }

    // --- INV-NBRS-7 ---
    @Test
    fun `INV-NBRS-7 given Ana listed once, when the same Neighbor Registered is applied again, then still listed once`() {
        val h = Harness()
        val event = registered(ana, "Ana Ruiz", "ana@example.org", now)
        h.apply(event, event)
        assertThat(h.list()).hasSize(1)
        assertThat(h.list().single().status).isEqualTo(NeighborStatus.ACTIVE)
    }

    @Test
    fun `INV-NBRS-7 given an email change, when Registered and the change are applied again, then the later email stands`() {
        val h = Harness()
        val registered = registered(ana, "Ana Ruiz", "ana@example.org", now)
        val changed = NeighborEmailChanged(ana, "ana.ruiz@example.net", now)
        h.apply(registered, changed, registered, changed)
        assertThat(h.list().single().email).isEqualTo("ana.ruiz@example.net")
    }

    @Test
    fun `INV-NBRS-7 given a suspension, when Neighbor Suspended and Reinstated are each applied twice, then the same as once`() {
        val h = Harness()
        val suspended = NeighborSuspended(ana, now)
        val reinstated = NeighborReinstated(ana, now)
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org", now), suspended, suspended)
        assertThat(h.list().single().status).isEqualTo(NeighborStatus.SUSPENDED)
        h.apply(reinstated, reinstated)
        assertThat(h.list().single().status).isEqualTo(NeighborStatus.ACTIVE)
    }

    @Test
    fun `INV-NBRS-7 given Suspended then Reinstated, when the earlier Suspended is redelivered, then still active`() {
        val h = Harness()
        val suspended = NeighborSuspended(ana, at(10, 6))
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org", now), suspended, NeighborReinstated(ana, at(10, 7)), suspended)
        val row = h.list().single()
        assertThat(row.status).isEqualTo(NeighborStatus.ACTIVE)
        assertThat(row.canSuspend).isTrue()
    }

    @Test
    fun `INV-NBRS-7 INV-NBRS-5 given two email changes, when the first is redelivered, then the later email stands`() {
        val h = Harness()
        val first = NeighborEmailChanged(ana, "a@example.net", at(10, 6))
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org", now), first, NeighborEmailChanged(ana, "b@example.net", at(10, 7)), first)
        assertThat(h.list().single().email).isEqualTo("b@example.net")
    }

    // --- INV-NBRS-5 ---
    @Test
    fun `INV-NBRS-5 given Ana active, when Neighbor Email Changed, then shows the new email, name unchanged, still active`() {
        val h = Harness()
        h.apply(
            registered(ana, "Ana Ruiz", "ana@example.org", now),
            NeighborEmailChanged(ana, "ana.ruiz@example.net", now),
        )
        val row = h.list().single()
        assertThat(row.name).isEqualTo("Ana Ruiz")
        assertThat(row.email).isEqualTo("ana.ruiz@example.net")
        assertThat(row.status).isEqualTo(NeighborStatus.ACTIVE)
    }

    // --- INV-NBRS-2, INV-NBRS-6 ---
    @Test
    fun `INV-NBRS-2 INV-NBRS-6 given Ana and Ben active, when Ana is suspended, then Ana suspended without Suspend and Ben unchanged`() {
        val h = Harness()
        h.apply(
            registered(ana, "Ana Ruiz", "ana@example.org", now),
            registered(ben, "Ben Ito", "ben@example.org", now),
            NeighborSuspended(ana, now),
        )
        val rows = h.list().associateBy { it.neighborId }
        assertThat(rows.getValue(ana).status).isEqualTo(NeighborStatus.SUSPENDED)
        assertThat(rows.getValue(ana).canSuspend).isFalse()
        assertThat(rows.getValue(ben).status).isEqualTo(NeighborStatus.ACTIVE)
        assertThat(rows.getValue(ben).canSuspend).isTrue()
    }

    @Test
    fun `INV-NBRS-2 INV-NBRS-6 given Ana suspended, when Neighbor Reinstated, then Ana active with Suspend offered`() {
        val h = Harness()
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org", now), NeighborSuspended(ana, now), NeighborReinstated(ana, now))
        val row = h.list().single()
        assertThat(row.status).isEqualTo(NeighborStatus.ACTIVE)
        assertThat(row.canSuspend).isTrue()
    }

    // --- INV-NBRS-3, INV-NBRS-6 ---
    @Test
    fun `INV-NBRS-3 INV-NBRS-6 given Ana active, when her account is closed, then still listed as closed without Suspend`() {
        val h = Harness()
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org", now), closed(ana))
        val row = h.list().single()
        assertThat(row.neighborId).isEqualTo(ana)
        assertThat(row.status).isEqualTo(NeighborStatus.CLOSED)
        assertThat(row.canSuspend).isFalse()
    }

    // --- INV-NBRS-4 ---
    @Test
    fun `INV-NBRS-4 given Ana suspended, when she closes her account, then shown as closed`() {
        val h = Harness()
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org", now), NeighborSuspended(ana, now), closedWhileSuspended(ana))
        assertThat(h.list().single().status).isEqualTo(NeighborStatus.CLOSED)
    }

    @Test
    fun `INV-NBRS-4 given Ana closed, when an earlier Suspended or Reinstated is applied again, then still closed`() {
        val h = Harness()
        val suspended = NeighborSuspended(ana, now)
        val reinstated = NeighborReinstated(ana, now)
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org", now), suspended, reinstated, closed(ana))
        h.apply(suspended)
        assertThat(h.list().single().status).isEqualTo(NeighborStatus.CLOSED)
        h.apply(reinstated)
        assertThat(h.list().single().status).isEqualTo(NeighborStatus.CLOSED)
    }

    // --- INV-NBRS-1, INV-NBRS-3 ---
    @Test
    fun `INV-NBRS-1 INV-NBRS-3 given Ana closed, when she signs up again, then two entries - the old closed and the new active`() {
        val h = Harness()
        h.apply(
            registered(ana, "Ana Ruiz", "ana@example.org", at(10, 1)),
            closed(ana),
            registered(ana2, "Ana Ruiz", "ana@example.org", at(10, 5)),
        )
        val rows = h.list()
        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.email }).containsOnly("ana@example.org")
        assertThat(rows[0].neighborId).isEqualTo(ana)
        assertThat(rows[0].status).isEqualTo(NeighborStatus.CLOSED)
        assertThat(rows[1].neighborId).isEqualTo(ana2)
        assertThat(rows[1].status).isEqualTo(NeighborStatus.ACTIVE)
        assertThat(rows[1].canSuspend).isTrue()
    }

    // --- INV-NBRS-8 ---
    @Test
    fun `INV-NBRS-8 given Ben on 2 Oct and Ana on 1 Oct closed then again on 5 Oct, when listed, then Ana Ana Ben`() {
        val h = Harness()
        h.apply(
            registered(ben, "Ben Ito", "ben@example.org", at(10, 2)),
            registered(ana, "Ana Ruiz", "ana@example.org", at(10, 1)),
            closed(ana),
            registered(ana2, "Ana Ruiz", "ana@example.org", at(10, 5)),
        )
        assertThat(h.list().map { it.neighborId }).containsExactly(ana, ana2, ben)
        assertThat(h.list().map { it.status })
            .containsExactly(NeighborStatus.CLOSED, NeighborStatus.ACTIVE, NeighborStatus.ACTIVE)
    }

    @Test
    fun `INV-NBRS-8 given names differing in case, when listed, then ordered ignoring case`() {
        val h = Harness()
        h.apply(
            registered(ben, "ben Ito", "ben@example.org", at(10, 1)),
            registered(ana, "Ana Ruiz", "ana@example.org", at(10, 2)),
            registered(cara, "Cara Diaz", "cara@example.org", at(10, 3)),
        )
        assertThat(h.list().map { it.neighborId }).containsExactly(ana, ben, cara)
    }

    // --- Listing the neighbors ---
    @Test
    fun `given Ana active, Ben suspended and Cara closed, when listed, then all three come back and only Ana offers Suspend`() {
        val h = Harness()
        h.apply(
            registered(ana, "Ana Ruiz", "ana@example.org", at(10, 1)),
            registered(ben, "Ben Ito", "ben@example.org", at(10, 2)),
            registered(cara, "Cara Diaz", "cara@example.org", at(10, 3)),
            NeighborSuspended(ben, now),
            closed(cara),
        )
        val rows = h.list().associateBy { it.neighborId }
        assertThat(rows).hasSize(3)
        assertThat(rows.getValue(ana).status).isEqualTo(NeighborStatus.ACTIVE)
        assertThat(rows.getValue(ben).status).isEqualTo(NeighborStatus.SUSPENDED)
        assertThat(rows.getValue(cara).status).isEqualTo(NeighborStatus.CLOSED)
        assertThat(h.list().filter { it.canSuspend }.map { it.neighborId }).containsExactly(ana)
    }

    // --- INV-NBRS-9 ---
    @Test
    fun `INV-NBRS-9 given entries, when a neighbor without the shed-manager role asks, then refused not-shed-manager`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller(neighborId = ben, isShedManager = false)) { requireNeighborsReader() }
            }
        assertThat(refused.reason).isEqualTo("not-shed-manager")
        assertThat(refused.message).isEqualTo("Only the shed manager can see this screen.")
    }

    @Test
    fun `INV-NBRS-9 given entries, when a caller with no sign-in asks, then refused not-signed-in`() {
        val refused = assertThrows<CommandRejected> { with(Caller.ANONYMOUS) { requireNeighborsReader() } }
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }

    @Test
    fun `INV-NBRS-9 given the shed manager, when asking, then allowed`() {
        with(Caller(neighborId = null, isShedManager = true)) { requireNeighborsReader() }
    }
}
