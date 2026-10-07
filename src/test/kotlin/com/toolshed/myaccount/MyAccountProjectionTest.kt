package com.toolshed.myaccount

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** My Account applied event by event, read through the view, with no framework in between. */
class MyAccountProjectionTest {
    private val t1 = Instant.parse("2026-10-01T09:00:00Z")
    private val t2 = Instant.parse("2026-10-02T09:00:00Z")

    private val n1 = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000a"))
    private val n2 = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000b"))

    private lateinit var projection: MyAccountProjection
    private lateinit var view: MyAccountView

    @BeforeEach
    fun setUp() {
        val repository = MyAccountRepository()
        projection = MyAccountProjection(repository)
        view = MyAccountView(repository)
    }

    private fun row(neighbor: NeighborId) = view.get(neighbor)!!

    @Test
    fun `INV-MYAC-2 INV-MYAC-3 INV-MYAC-9 INV-MYAC-11 - given N1 registers, then it is active with both actions offered`() {
        projection.on(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", t1))
        val mine = row(n1)
        assertEquals("Ana Ruiz", mine.name)
        assertEquals("ana@example.org", mine.email)
        assertEquals("active", mine.status)
        assertTrue(mine.canChangeEmail && mine.canCloseAccount)
    }

    @Test
    fun `INV-MYAC-2 - given N1 changes email, then the new email shows and it stays active`() {
        projection.on(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborEmailChanged(n1, "ana.ruiz@example.net", t2))
        assertEquals("ana.ruiz@example.net", row(n1).email)
        assertEquals("active", row(n1).status)
    }

    @Test
    fun `INV-MYAC-4 - given N1 is suspended, then it shows suspended and both actions are still offered`() {
        projection.on(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborSuspended(n1, t2))
        assertEquals("suspended", row(n1).status)
        assertTrue(row(n1).canChangeEmail && row(n1).canCloseAccount)
    }

    @Test
    fun `INV-MYAC-4 - given N1 is reinstated, then it shows active again`() {
        projection.on(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborSuspended(n1, t2))
        projection.on(NeighborReinstated(n1, t2))
        assertEquals("active", row(n1).status)
    }

    @Test
    fun `INV-MYAC-2 INV-MYAC-4 - given N1 was reinstated, when its email changes, then the new email shows, active`() {
        projection.on(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborSuspended(n1, t2))
        projection.on(NeighborReinstated(n1, t2))
        projection.on(NeighborEmailChanged(n1, "ana.ruiz@example.net", t2))
        assertEquals("ana.ruiz@example.net", row(n1).email)
        assertEquals("active", row(n1).status)
    }

    @Test
    fun `INV-MYAC-5 INV-MYAC-6 - given N1 is active, when closed, then closed with neither action offered`() {
        projection.on(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborAccountClosed(n1, false, t2))
        assertEquals("closed", row(n1).status)
        assertTrue(!row(n1).canChangeEmail && !row(n1).canCloseAccount)
    }

    @Test
    fun `INV-MYAC-5 - given N1 is suspended, when closed, then closed and not suspended`() {
        projection.on(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborSuspended(n1, t2))
        projection.on(NeighborAccountClosed(n1, true, t2))
        assertEquals("closed", row(n1).status)
    }

    @Test
    fun `INV-MYAC-8 - given N1 closed, when a new account N4 registers for the same person, then N4 shows active and N1 stays closed`() {
        val n4 = NeighborId(UUID.randomUUID())
        projection.on(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborAccountClosed(n1, false, t2))
        projection.on(NeighborRegistered(n4, "Ana Ruiz", "ana@example.org", t2))
        assertEquals("active", row(n4).status)
        assertEquals("closed", row(n1).status)
    }

    @Test
    fun `INV-MYAC-1 - given N1 and N2 both registered, when N1 is asked for, then only N1 is returned`() {
        projection.on(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborRegistered(n2, "Ben Ito", "ben@example.org", t1))
        assertEquals(n1, row(n1).neighborId)
        assertEquals("Ana Ruiz", row(n1).name)
    }

    @Test
    fun `INV-MYAC-1 INV-MYAC-7 - given N1 active, when N2 is suspended, then N1 is unchanged`() {
        projection.on(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborRegistered(n2, "Ben Ito", "ben@example.org", t1))
        projection.on(NeighborSuspended(n2, t2))
        assertEquals("active", row(n1).status)
    }

    @Test
    fun `INV-MYAC-10 - given N1 registered, when the same registration is applied again, then one account, unchanged`() {
        projection.on(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", t1))
        projection.on(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", t1))
        assertEquals("active", row(n1).status)
        assertEquals("ana@example.org", row(n1).email)
    }

    @Test
    fun `INV-MYAC-1 - given no account N9, when asked for, then there is no account`() {
        assertNull(view.get(NeighborId(UUID.randomUUID())))
    }
}
