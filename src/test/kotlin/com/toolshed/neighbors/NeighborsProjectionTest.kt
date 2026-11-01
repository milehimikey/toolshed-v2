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
    private val ana2 = NeighborId(uuid("n1b"))
    private val ben = NeighborId(uuid("n2"))
    private val cara = NeighborId(uuid("n3"))

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

    private fun closed(id: NeighborId) = NeighborAccountClosed(id, whileSuspended = false, closedAt = at(10, 20))

    @Test
    fun `INV-NBRS-1 INV-NBRS-2 INV-NBRS-5 INV-NBRS-6 given none, when Ana Ruiz registers, then listed active with Suspend offered`() {
        val h = Harness()
        assertThat(h.list()).isEmpty()
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org", at(10, 5)))
        val row = h.list().single()
        assertThat(row.neighborId).isEqualTo(ana)
        assertThat(row.name).isEqualTo("Ana Ruiz")
        assertThat(row.email).isEqualTo("ana@example.org")
        assertThat(row.registeredAt).isEqualTo(at(10, 5))
        assertThat(row.status).isEqualTo("active")
        assertThat(row.canSuspend).isTrue()
    }

    @Test
    fun `INV-NBRS-1 given Ana Ruiz listed, when a different account also named Ana Ruiz registers, then both are listed separately`() {
        val h = Harness()
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org"), registered(ana2, "Ana Ruiz", "ana.r@example.net"))
        assertThat(h.list().map { it.neighborId }).containsExactlyInAnyOrder(ana, ana2)
    }

    @Test
    fun `INV-NBRS-7 given Ana listed once, when the same Neighbor Registered is applied again, then Ana is still listed once`() {
        val h = Harness()
        val event = registered(ana, "Ana Ruiz", "ana@example.org")
        h.apply(event, event)
        assertThat(h.list()).hasSize(1)
    }

    @Test
    fun `INV-NBRS-7 given an email change, when Neighbor Registered is applied again, then the changed email stands`() {
        val h = Harness()
        val event = registered(ana, "Ana Ruiz", "ana@example.org")
        h.apply(event, NeighborEmailChanged(ana, "ana.ruiz@example.net", at(10, 6)), event)
        assertThat(h.list().single().email).isEqualTo("ana.ruiz@example.net")
    }

    @Test
    fun `INV-NBRS-7 given any event applied twice, then the screen is as if applied once`() {
        val h = Harness()
        val change = NeighborEmailChanged(ana, "new@example.org", at(10, 6))
        val suspend = NeighborSuspended(ana, at(10, 7))
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org"), change, change, suspend, suspend)
        val row = h.list().single()
        assertThat(row.email).isEqualTo("new@example.org")
        assertThat(row.status).isEqualTo("suspended")
        h.apply(closed(ana), closed(ana))
        assertThat(h.list().single().status).isEqualTo("closed")
    }

    @Test
    fun `INV-NBRS-5 given Ana active, when Neighbor Email Changed, then listed with the new email still active`() {
        val h = Harness()
        h.apply(registered(ana, "Ana Ruiz", "ana@example.org"), NeighborEmailChanged(ana, "ana.ruiz@example.net", at(10, 6)))
        val row = h.list().single()
        assertThat(row.name).isEqualTo("Ana Ruiz")
        assertThat(row.email).isEqualTo("ana.ruiz@example.net")
        assertThat(row.status).isEqualTo("active")
    }

    @Test
    fun `INV-NBRS-2 INV-NBRS-6 given Ana and Ben active, when Ana suspended, then no Suspend for Ana and Ben unchanged`() {
        val h = Harness()
        h.apply(registered(ana, "Ana", "a@example.org"), registered(ben, "Ben", "b@example.org"), NeighborSuspended(ana, at(10, 7)))
        val rows = h.list().associateBy { it.neighborId }
        assertThat(rows.getValue(ana).status).isEqualTo("suspended")
        assertThat(rows.getValue(ana).canSuspend).isFalse()
        assertThat(rows.getValue(ben).status).isEqualTo("active")
        assertThat(rows.getValue(ben).canSuspend).isTrue()
    }

    @Test
    fun `INV-NBRS-2 INV-NBRS-6 given Ana suspended, when Neighbor Reinstated, then Ana active again with Suspend offered`() {
        val h = Harness()
        h.apply(registered(ana, "Ana", "a@example.org"), NeighborSuspended(ana, at(10, 7)), NeighborReinstated(ana, at(10, 8)))
        val row = h.list().single()
        assertThat(row.status).isEqualTo("active")
        assertThat(row.canSuspend).isTrue()
    }

    @Test
    fun `INV-NBRS-3 INV-NBRS-6 given Ana active, when account closed, then still listed as closed without Suspend`() {
        val h = Harness()
        h.apply(registered(ana, "Ana", "a@example.org"), closed(ana))
        val row = h.list().single()
        assertThat(row.status).isEqualTo("closed")
        assertThat(row.canSuspend).isFalse()
    }

    @Test
    fun `INV-NBRS-4 given Ana suspended, when Neighbor Account Closed, then Ana shown as closed`() {
        val h = Harness()
        h.apply(registered(ana, "Ana", "a@example.org"), NeighborSuspended(ana, at(10, 7)), closed(ana))
        assertThat(h.list().single().status).isEqualTo("closed")
    }

    @Test
    fun `INV-NBRS-4 given Ana closed, when an earlier Suspended or Reinstated is applied again, then still closed`() {
        val h = Harness()
        val suspended = NeighborSuspended(ana, at(10, 7))
        val reinstated = NeighborReinstated(ana, at(10, 8))
        h.apply(registered(ana, "Ana", "a@example.org"), suspended, reinstated, closed(ana))
        h.apply(suspended)
        assertThat(h.list().single().status).isEqualTo("closed")
        h.apply(reinstated)
        assertThat(h.list().single().status).isEqualTo("closed")
        assertThat(h.list().single().canSuspend).isFalse()
    }

    @Test
    fun `INV-NBRS-1 INV-NBRS-3 given Ana closed, when Ana Ruiz registers again, then old closed and new active with Suspend`() {
        val h = Harness()
        h.apply(
            registered(ana, "Ana Ruiz", "ana@example.org", at(10, 1)),
            closed(ana),
            registered(ana2, "Ana Ruiz", "ana@example.org", at(10, 5)),
        )
        val rows = h.list()
        assertThat(rows.map { it.neighborId to it.status }).containsExactly(ana to "closed", ana2 to "active")
        assertThat(rows.last().canSuspend).isTrue()
    }

    @Test
    fun `INV-NBRS-8 given Ben Ito and Ana Ruiz twice, when listed, then Ana by sign-up then Ben`() {
        val h = Harness()
        h.apply(
            registered(ben, "Ben Ito", "ben@example.org", at(10, 2)),
            registered(ana, "Ana Ruiz", "ana@example.org", at(10, 1)),
            closed(ana),
            registered(ana2, "Ana Ruiz", "ana@example.org", at(10, 5)),
        )
        assertThat(h.list().map { it.neighborId }).containsExactly(ana, ana2, ben)
    }

    @Test
    fun `INV-NBRS-8 given names differing in case, when listed, then ordered ignoring case`() {
        val h = Harness()
        h.apply(registered(ben, "ben", "b@example.org", at(10, 1)), registered(ana, "Carl", "c@example.org", at(10, 1)))
        assertThat(h.list().map { it.name }).containsExactly("ben", "Carl")
    }

    @Test
    fun `INV-NBRS-6 given Ana active, Ben suspended, Cara closed, when listed, then only Ana offers Suspend`() {
        val h = Harness()
        h.apply(
            registered(ana, "Ana", "a@example.org"),
            registered(ben, "Ben", "b@example.org"),
            registered(cara, "Cara", "c@example.org"),
            NeighborSuspended(ben, at(10, 7)),
            closed(cara),
        )
        val rows = h.list()
        assertThat(rows.map { it.name to it.status }).containsExactly("Ana" to "active", "Ben" to "suspended", "Cara" to "closed")
        assertThat(rows.map { it.canSuspend }).containsExactly(true, false, false)
    }

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
