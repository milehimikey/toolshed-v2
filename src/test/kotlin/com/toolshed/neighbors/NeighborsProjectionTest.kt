package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The projection and the list, applied event by event, with no framework in between. */
class NeighborsProjectionTest {
    private val t1 = Instant.parse("2026-10-01T09:00:00Z")
    private val t2 = Instant.parse("2026-10-02T09:00:00Z")
    private val t5 = Instant.parse("2026-10-05T09:00:00Z")

    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000a"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000b"))
    private val cara = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000c"))

    private lateinit var repository: NeighborsRepository
    private lateinit var projection: NeighborsProjection
    private lateinit var view: NeighborsView

    @BeforeEach
    fun setUp() {
        repository = NeighborsRepository()
        projection = NeighborsProjection(repository)
        view = NeighborsView(repository)
    }

    private fun row(neighbor: NeighborId) = view.list().single { it.neighborId == neighbor }

    @Test
    fun `INV-NBRS-1 - given no neighbors, when Ana registers, then one entry, active, with Suspend offered`() {
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        val rows = view.list()
        assertEquals(1, rows.size)
        assertEquals("active", rows.single().status)
        assertTrue(rows.single().canSuspend)
    }

    @Test
    fun `INV-NBRS-1 - given two accounts both named Ana Ruiz, when both register, then two separate entries`() {
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborRegistered(cara, "Ana Ruiz", "ana.r@example.net", t2))
        assertEquals(2, view.list().size)
    }

    @Test
    fun `INV-NBRS-7 - given Ana is listed, when the same registration is applied again, then still one entry`() {
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        assertEquals(1, view.list().size)
    }

    @Test
    fun `INV-NBRS-5 - given Ana, when her email changes, then the list shows the new email, still active`() {
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborEmailChanged(ana, "ana.ruiz@example.net", t2))
        val entry = row(ana)
        assertEquals("ana.ruiz@example.net", entry.email)
        assertEquals("active", entry.status)
    }

    @Test
    fun `INV-NBRS-2 INV-NBRS-6 - given Ana and Ben active, when Ana is suspended, then Ana has no Suspend and Ben is unchanged`() {
        projection.on(NeighborRegistered(ana, "Ana", "ana@example.org", t1))
        projection.on(NeighborRegistered(ben, "Ben", "ben@example.org", t1))
        projection.on(NeighborSuspended(ana, t2))
        assertEquals("suspended", row(ana).status)
        assertEquals(false, row(ana).canSuspend)
        assertEquals("active", row(ben).status)
        assertTrue(row(ben).canSuspend)
    }

    @Test
    fun `INV-NBRS-2 INV-NBRS-6 - given Ana is suspended, when she is reinstated, then active again with Suspend offered`() {
        projection.on(NeighborRegistered(ana, "Ana", "ana@example.org", t1))
        projection.on(NeighborSuspended(ana, t2))
        projection.on(NeighborReinstated(ana, t5))
        assertEquals("active", row(ana).status)
        assertTrue(row(ana).canSuspend)
    }

    @Test
    fun `INV-NBRS-3 INV-NBRS-6 - given Ana is active, when her account closes, then she stays listed as closed with no Suspend`() {
        projection.on(NeighborRegistered(ana, "Ana", "ana@example.org", t1))
        projection.on(NeighborAccountClosed(ana, false, t2))
        assertEquals("closed", row(ana).status)
        assertEquals(false, row(ana).canSuspend)
    }

    @Test
    fun `INV-NBRS-4 - given Ana suspended then closed, when closed, then shown as closed, not suspended`() {
        projection.on(NeighborRegistered(ana, "Ana", "ana@example.org", t1))
        projection.on(NeighborSuspended(ana, t2))
        projection.on(NeighborAccountClosed(ana, true, t5))
        assertEquals("closed", row(ana).status)
    }

    @Test
    fun `INV-NBRS-4 - given Ana closed, when an earlier suspension or reinstatement is applied again, then still closed`() {
        projection.on(NeighborRegistered(ana, "Ana", "ana@example.org", t1))
        projection.on(NeighborAccountClosed(ana, false, t2))
        projection.on(NeighborSuspended(ana, t2))
        projection.on(NeighborReinstated(ana, t5))
        assertEquals("closed", row(ana).status)
    }

    @Test
    fun `INV-NBRS-1 INV-NBRS-3 - given Ana closed, when she signs up again, then two entries, one closed, one active`() {
        val newAna = NeighborId(UUID.randomUUID())
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborAccountClosed(ana, false, t2))
        projection.on(NeighborRegistered(newAna, "Ana Ruiz", "ana@example.org", t5))
        val rows = view.list()
        assertEquals(2, rows.size)
        assertEquals("closed", rows.single { it.neighborId == ana }.status)
        assertEquals("active", rows.single { it.neighborId == newAna }.status)
    }

    @Test
    fun `INV-NBRS-8 - given Ana closed then signed up again, and Ben between, when listed, then Ana closed, Ana again, Ben`() {
        val newAna = NeighborId(UUID.randomUUID())
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborAccountClosed(ana, false, t2))
        projection.on(NeighborRegistered(ben, "Ben Ito", "ben@example.org", t2))
        projection.on(NeighborRegistered(newAna, "Ana Ruiz", "ana@example.org", t5))
        val ordered = view.list().map { it.neighborId }
        assertEquals(listOf(ana, newAna, ben), ordered)
    }

    @Test
    fun `INV-NBRS-8 - given names differing only in case, when listed, then they sort by name ignoring case`() {
        projection.on(NeighborRegistered(ben, "ben ito", "ben@example.org", t1))
        projection.on(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        assertEquals(listOf(ana, ben), view.list().map { it.neighborId })
    }

    @Test
    fun `INV-NBRS-2 - given Ben suspended, Ana active and Cara closed, when listed, then all three come back with their status`() {
        projection.on(NeighborRegistered(ana, "Ana", "ana@example.org", t1))
        projection.on(NeighborRegistered(ben, "Ben", "ben@example.org", t1))
        projection.on(NeighborRegistered(cara, "Cara", "cara@example.org", t1))
        projection.on(NeighborSuspended(ben, t2))
        projection.on(NeighborAccountClosed(cara, false, t2))
        val statuses = view.list().associate { it.neighborId to it.status }
        assertEquals(mapOf(ana to "active", ben to "suspended", cara to "closed"), statuses)
    }
}
