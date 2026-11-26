package com.toolshed.neighbors

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
        registeredAt: Instant = at(10, 1),
    ) = NeighborRegistered(id, name, email, registeredAt)

    private fun closed(id: NeighborId) = NeighborAccountClosed(id, false, at(10, 20))

    // --- INV-NBRS-1, INV-NBRS-2, INV-NBRS-5, INV-NBRS-6 ---

    @Test
    fun `INV-NBRS-1 INV-NBRS-2 INV-NBRS-5 INV-NBRS-6 given no neighbors, when Ana registers, then listed active with Suspend offered`() {
        val h = Harness()
        assertThat(h.list()).isEmpty()
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org"))
        assertThat(h.list()).containsExactly(
            NeighborRow(ana, "Ana Ruiz", "ana@example.org", at(10, 1), NeighborStatus.ACTIVE, true),
        )
    }

    // --- INV-NBRS-1 ---

    @Test
    fun `INV-NBRS-1 given Ana listed, when a different account also named Ana Ruiz registers, then both are listed`() {
        val h = Harness()
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org"), registered(ana2, "Ana Ruiz", "ana.r@example.net"))
        assertThat(h.list().map { it.neighborId }).containsExactly(ana, ana2)
    }

    // --- INV-NBRS-7 ---

    @Test
    fun `INV-NBRS-7 given Ana listed once, when the same Neighbor Registered is applied again, then still listed once`() {
        val h = Harness()
        val event = registered(ana, "Ana Ruiz", "ana@example.org")
        h.apply(event, NeighborEmailChanged(ana, "new@example.org", at(10, 2)), event)
        assertThat(h.list()).hasSize(1)
        assertThat(h.list().single().email).isEqualTo("new@example.org")
    }

    @Test
    fun `INV-NBRS-7 given suspended then reinstated, when the earlier Neighbor Suspended is applied again, then still active`() {
        val h = Harness()
        val suspended = NeighborSuspended(ana, at(10, 3))
        h.apply(registered(ana, "Ana Ruiz", "a@example.org"), suspended, NeighborReinstated(ana, at(10, 4)), suspended)
        assertThat(h.list().single().status).isEqualTo(NeighborStatus.ACTIVE)
    }

    // --- INV-NBRS-5 ---

    @Test
    fun `INV-NBRS-5 given Ana active, when her email changes, then listed with the new email and still active`() {
        val h = Harness()
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org"), NeighborEmailChanged(ana, "ana.ruiz@example.net", at(10, 2)))
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
            registered(ana, "Ana Ruiz", "ana@example.org"),
            registered(ben, "Ben Ito", "ben@example.org"),
            NeighborSuspended(ana, at(10, 3)),
        )
        val (a, b) = h.list()
        assertThat(a.status).isEqualTo(NeighborStatus.SUSPENDED)
        assertThat(a.canSuspend).isFalse()
        assertThat(b.status).isEqualTo(NeighborStatus.ACTIVE)
        assertThat(b.canSuspend).isTrue()
    }

    @Test
    fun `INV-NBRS-2 INV-NBRS-6 given Ana suspended, when she is reinstated, then active again with Suspend offered`() {
        val h = Harness()
        h.apply(registered(ana, "Ana Ruiz", "a@example.org"), NeighborSuspended(ana, at(10, 3)), NeighborReinstated(ana, at(10, 4)))
        val row = h.list().single()
        assertThat(row.status).isEqualTo(NeighborStatus.ACTIVE)
        assertThat(row.canSuspend).isTrue()
    }

    // --- INV-NBRS-3, INV-NBRS-6 ---

    @Test
    fun `INV-NBRS-3 INV-NBRS-6 given Ana active, when her account closes, then still listed as closed without Suspend`() {
        val h = Harness()
        h.apply(registered(ana, "Ana Ruiz", "a@example.org"), closed(ana))
        val row = h.list().single()
        assertThat(row.status).isEqualTo(NeighborStatus.CLOSED)
        assertThat(row.canSuspend).isFalse()
    }

    // --- INV-NBRS-4 ---

    @Test
    fun `INV-NBRS-4 given Ana suspended, when her account closes, then shown as closed`() {
        val h = Harness()
        h.apply(registered(ana, "Ana Ruiz", "a@example.org"), NeighborSuspended(ana, at(10, 3)), closed(ana))
        assertThat(h.list().single().status).isEqualTo(NeighborStatus.CLOSED)
    }

    @Test
    fun `INV-NBRS-4 given Ana closed, when an earlier suspension or reinstatement is applied again, then still closed`() {
        val h = Harness()
        val suspended = NeighborSuspended(ana, at(10, 3))
        val reinstated = NeighborReinstated(ana, at(10, 4))
        h.apply(registered(ana, "Ana Ruiz", "a@example.org"), suspended, reinstated, closed(ana), suspended, reinstated)
        assertThat(h.list().single().status).isEqualTo(NeighborStatus.CLOSED)
    }

    // --- INV-NBRS-1, INV-NBRS-3 ---

    @Test
    fun `INV-NBRS-1 INV-NBRS-3 given Ana closed, when she signs up again, then two entries, old closed and new active`() {
        val h = Harness()
        h.apply(
            registered(ana, "Ana Ruiz", "ana@example.org"),
            closed(ana),
            registered(ana2, "Ana Ruiz", "ana@example.org", at(10, 5)),
        )
        val rows = h.list()
        assertThat(rows.map { it.status }).containsExactly(NeighborStatus.CLOSED, NeighborStatus.ACTIVE)
        assertThat(rows.map { it.canSuspend }).containsExactly(false, true)
    }

    // --- INV-NBRS-8 ---

    @Test
    fun `INV-NBRS-8 given Ben and a re-registered Ana, when listed, then Ana 1 Oct, Ana 5 Oct, then Ben`() {
        val h = Harness()
        h.apply(
            registered(ben, "Ben Ito", "ben@example.org", at(10, 2)),
            registered(ana, "Ana Ruiz", "ana@example.org", at(10, 1)),
            closed(ana),
            registered(ana2, "ana ruiz", "ana@example.org", at(10, 5)),
        )
        assertThat(h.list().map { it.neighborId }).containsExactly(ana, ana2, ben)
    }

    // --- Listing the neighbors ---

    @Test
    fun `given Ana active, Ben suspended and Cara closed, when listed, then all three return and only Ana offers Suspend`() {
        val h = Harness()
        h.apply(
            registered(ana, "Ana Ruiz", "ana@example.org"),
            registered(ben, "Ben Ito", "ben@example.org"),
            registered(cara, "Cara Diaz", "cara@example.org"),
            NeighborSuspended(ben, at(10, 3)),
            closed(cara),
        )
        val rows = h.list()
        assertThat(rows.map { it.status }).containsExactly(NeighborStatus.ACTIVE, NeighborStatus.SUSPENDED, NeighborStatus.CLOSED)
        assertThat(rows.map { it.canSuspend }).containsExactly(true, false, false)
    }

    // --- INV-NBRS-9 ---

    @Test
    fun `INV-NBRS-9 given entries, when a neighbor without the role asks, then refused not-shed-manager`() {
        val e = assertThrows<com.toolshed.axon.CommandRejected> { Caller(neighborId = ana, isShedManager = false).requireNeighborsReader() }
        assertThat(e.reason).isEqualTo("not-shed-manager")
        assertThat(e.message).isEqualTo("Only the shed manager can see this screen.")
    }

    @Test
    fun `INV-NBRS-9 given entries, when a caller with no sign-in asks, then refused not-signed-in`() {
        val e =
            assertThrows<com.toolshed.axon.CommandRejected> { Caller(neighborId = null, isShedManager = false).requireNeighborsReader() }
        assertThat(e.reason).isEqualTo("not-signed-in")
        assertThat(e.message).isEqualTo("Please sign in.")
    }
}
