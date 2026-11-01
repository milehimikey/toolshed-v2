package com.toolshed.myaccount

import com.toolshed.axon.NeighborId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** The view's rules, applied to the in-memory read model: status order, offered actions, idempotency, isolation. */
class MyAccountProjectionTest {
    private val n1 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val n2 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
    private val n4 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000004"))
    private val n9 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000009"))

    private val now = Instant.parse("2026-10-06T04:00:00Z")

    private class Harness {
        val repository = MyAccountRepository()
        val projection = MyAccountProjection(repository)

        fun find(id: NeighborId): MyAccountRow = requireNotNull(repository.find(id)) { "no account $id" }
    }

    private fun Harness.register(
        id: NeighborId,
        name: String,
        email: String,
    ) = projection.on(NeighborRegistered(id, name, email, now))

    private fun Harness.changeEmail(
        id: NeighborId,
        email: String,
    ) = projection.on(NeighborEmailChanged(id, email, now))

    private fun Harness.suspend(id: NeighborId) = projection.on(NeighborSuspended(id, now))

    private fun Harness.reinstate(id: NeighborId) = projection.on(NeighborReinstated(id, now))

    private fun Harness.close(id: NeighborId) = projection.on(NeighborAccountClosed(id, false, now))

    private fun registered(): Harness = Harness().also { it.register(n1, "Ana Ruiz", "ana@example.org") }

    // --- Scenario: Neighbor registered (INV-MYAC-2, INV-MYAC-3, INV-MYAC-9, INV-MYAC-11) ---

    @Test
    fun `INV-MYAC-2 INV-MYAC-3 INV-MYAC-9 INV-MYAC-11 - given no account, when Neighbor Registered, then active, both actions offered`() {
        val row = registered().find(n1)
        assertThat(row.name).isEqualTo("Ana Ruiz")
        assertThat(row.email).isEqualTo("ana@example.org")
        assertThat(row.status).isEqualTo("active")
        assertThat(row.canChangeEmail).isTrue()
        assertThat(row.canCloseAccount).isTrue()
    }

    // --- INV-MYAC-2: email changed ---

    @Test
    fun `INV-MYAC-2 - given active Ana, when Neighbor Email Changed, then the new email shows and the name is unchanged`() {
        val r = registered()
        r.changeEmail(n1, "ana.ruiz@example.net")
        val row = r.find(n1)
        assertThat(row.name).isEqualTo("Ana Ruiz")
        assertThat(row.email).isEqualTo("ana.ruiz@example.net")
        assertThat(row.status).isEqualTo("active")
    }

    // --- INV-MYAC-4, INV-MYAC-9, INV-MYAC-11: suspension ---

    @Test
    fun `INV-MYAC-4 INV-MYAC-9 INV-MYAC-11 - given active, when Neighbor Suspended, then suspended with both actions still offered`() {
        val r = registered()
        r.suspend(n1)
        val row = r.find(n1)
        assertThat(row.status).isEqualTo("suspended")
        assertThat(row.canChangeEmail).isTrue()
        assertThat(row.canCloseAccount).isTrue()
    }

    @Test
    fun `INV-MYAC-4 - given suspended, when Neighbor Reinstated, then active again`() {
        val r = registered()
        r.suspend(n1)
        r.reinstate(n1)
        assertThat(r.find(n1).status).isEqualTo("active")
    }

    @Test
    fun `INV-MYAC-2 INV-MYAC-4 - given suspended then reinstated, when Neighbor Email Changed, then the new email shows and active`() {
        val r = registered()
        r.suspend(n1)
        r.reinstate(n1)
        r.changeEmail(n1, "ana.ruiz@example.net")
        val row = r.find(n1)
        assertThat(row.email).isEqualTo("ana.ruiz@example.net")
        assertThat(row.status).isEqualTo("active")
    }

    // --- INV-MYAC-5, INV-MYAC-6: closed ---

    @Test
    fun `INV-MYAC-5 INV-MYAC-6 - given active, when Neighbor Account Closed, then closed with neither action offered but still readable`() {
        val r = registered()
        r.close(n1)
        val row = r.find(n1)
        assertThat(row.status).isEqualTo("closed")
        assertThat(row.canChangeEmail).isFalse()
        assertThat(row.canCloseAccount).isFalse()
    }

    @Test
    fun `INV-MYAC-5 - given suspended, when Neighbor Account Closed, then closed not suspended`() {
        val r = registered()
        r.suspend(n1)
        r.close(n1)
        assertThat(r.find(n1).status).isEqualTo("closed")
    }

    @Test
    fun `INV-MYAC-5 - given closed, when a replayed suspend reinstate or email change arrives, then it stays closed and unchanged`() {
        val r = registered()
        r.close(n1)
        r.suspend(n1)
        r.reinstate(n1)
        r.changeEmail(n1, "other@example.org")
        val row = r.find(n1)
        assertThat(row.status).isEqualTo("closed")
        assertThat(row.email).isEqualTo("ana@example.org")
    }

    // --- INV-MYAC-8: signed up again ---

    @Test
    fun `INV-MYAC-8 - given closed N1, when Neighbor Registered for new N4, then N4 is active and N1 is still closed`() {
        val r = registered()
        r.close(n1)
        r.register(n4, "Ana Ruiz", "ana@example.org")
        val fresh = r.find(n4)
        assertThat(fresh.neighborId).isEqualTo(n4)
        assertThat(fresh.status).isEqualTo("active")
        assertThat(r.find(n1).status).isEqualTo("closed")
    }

    // --- INV-MYAC-1, INV-MYAC-7: isolation ---

    @Test
    fun `INV-MYAC-1 - given Ana and Ben registered, when asked for N1, then only Ana's account is returned`() {
        val r = registered()
        r.register(n2, "Ben Ortiz", "ben@example.org")
        assertThat(r.find(n1).name).isEqualTo("Ana Ruiz")
        assertThat(r.find(n2).name).isEqualTo("Ben Ortiz")
    }

    @Test
    fun `INV-MYAC-1 INV-MYAC-7 - given active N1, when Neighbor Suspended for N2, then N1 is unchanged`() {
        val r = registered()
        r.register(n2, "Ben Ortiz", "ben@example.org")
        r.suspend(n2)
        assertThat(r.find(n1).status).isEqualTo("active")
        assertThat(r.find(n2).status).isEqualTo("suspended")
    }

    @Test
    fun `INV-MYAC-1 - given no account N9, when asked for N9, then not found`() {
        assertThat(registered().repository.find(n9)).isNull()
    }

    // --- INV-MYAC-10: idempotency ---

    @Test
    fun `INV-MYAC-10 - given active N1, when the same Neighbor Registered is applied again, then unchanged and still one account`() {
        val r = registered()
        val before = r.repository.find(n1)
        r.register(n1, "Ana Ruiz", "ana@example.org")
        assertThat(r.repository.find(n1)).isEqualTo(before)
    }

    @Test
    fun `INV-MYAC-10 - given a replayed registration after changes, then the account keeps its current facts`() {
        val r = registered()
        r.changeEmail(n1, "ana.ruiz@example.net")
        r.suspend(n1)
        r.register(n1, "Ana Ruiz", "ana@example.org")
        r.suspend(n1)
        r.changeEmail(n1, "ana.ruiz@example.net")
        val row = r.find(n1)
        assertThat(row.email).isEqualTo("ana.ruiz@example.net")
        assertThat(row.status).isEqualTo("suspended")
    }
}
