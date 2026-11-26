package com.toolshed.myaccount

import com.toolshed.axon.NeighborId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/** The view's rules, applied to the in-memory read model: one row per account, status rules, offered actions. */
class MyAccountProjectionTest {
    private val n1 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val n2 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
    private val n4 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000004"))
    private val n9 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000009"))

    private fun registered(): MyAccountRepository = MyAccountRepository().also { it.register(n1, "Ana Ruiz", "ana@example.org") }

    // --- INV-MYAC-2, INV-MYAC-3, INV-MYAC-9, INV-MYAC-11: neighbor registered ---

    @Test
    fun `INV-MYAC-2 INV-MYAC-3 INV-MYAC-9 INV-MYAC-11 - given no account, when Neighbor Registered, then active, both offered`() {
        val account = registered().find(n1)

        val expected = MyAccount(n1, "Ana Ruiz", "ana@example.org", "active", canChangeEmail = true, canCloseAccount = true)
        assertThat(account).isEqualTo(expected)
    }

    // --- INV-MYAC-2: email changed ---

    @Test
    fun `INV-MYAC-2 - given Ana active, when Neighbor Email Changed, then the new email shows and the name is unchanged`() {
        val r = registered()
        r.changeEmail(n1, "ana.ruiz@example.net")

        val account = r.find(n1)!!
        assertThat(account.name).isEqualTo("Ana Ruiz")
        assertThat(account.email).isEqualTo("ana.ruiz@example.net")
        assertThat(account.status).isEqualTo("active")
    }

    // --- INV-MYAC-4, INV-MYAC-9, INV-MYAC-11: suspended and reinstated ---

    @Test
    fun `INV-MYAC-4 INV-MYAC-9 INV-MYAC-11 - given active, when Neighbor Suspended, then suspended with both actions still offered`() {
        val r = registered()
        r.suspend(n1)

        val account = r.find(n1)!!
        assertThat(account.status).isEqualTo("suspended")
        assertThat(account.canChangeEmail).isTrue()
        assertThat(account.canCloseAccount).isTrue()
    }

    @Test
    fun `INV-MYAC-4 - given suspended, when Neighbor Reinstated, then active again`() {
        val r = registered()
        r.suspend(n1)
        r.reinstate(n1)

        assertThat(r.find(n1)!!.status).isEqualTo("active")
    }

    @Test
    fun `INV-MYAC-2 INV-MYAC-4 - given suspended then reinstated, when Neighbor Email Changed, then the new email shows and active`() {
        val r = registered()
        r.suspend(n1)
        r.reinstate(n1)
        r.changeEmail(n1, "ana.ruiz@example.net")

        val account = r.find(n1)!!
        assertThat(account.email).isEqualTo("ana.ruiz@example.net")
        assertThat(account.status).isEqualTo("active")
    }

    // --- INV-MYAC-5, INV-MYAC-6, INV-MYAC-9, INV-MYAC-11: closed ---

    @Test
    fun `INV-MYAC-5 INV-MYAC-6 INV-MYAC-9 INV-MYAC-11 - given active, when Account Closed, then closed, neither offered, readable`() {
        val r = registered()
        r.close(n1)

        val account = r.find(n1)!!
        assertThat(account.status).isEqualTo("closed")
        assertThat(account.canChangeEmail).isFalse()
        assertThat(account.canCloseAccount).isFalse()
        assertThat(account.email).isEqualTo("ana@example.org")
    }

    @Test
    fun `INV-MYAC-5 - given suspended, when Neighbor Account Closed, then closed not suspended`() {
        val r = registered()
        r.suspend(n1)
        r.close(n1)

        assertThat(r.find(n1)!!.status).isEqualTo("closed")
    }

    @Test
    fun `INV-MYAC-5 - given closed, when a replayed Suspended, Reinstated or Email Changed arrives, then nothing changes`() {
        val r = registered()
        r.close(n1)
        val before = r.find(n1)

        r.suspend(n1)
        r.reinstate(n1)
        r.changeEmail(n1, "other@example.org")

        assertThat(r.find(n1)).isEqualTo(before)
    }

    // --- INV-MYAC-8: signed up again after closing ---

    @Test
    fun `INV-MYAC-8 - given N1 closed, when N4 registers with the same name and email, then N4 is active and N1 still closed`() {
        val r = registered()
        r.close(n1)
        r.register(n4, "Ana Ruiz", "ana@example.org")

        assertThat(r.find(n4)!!.status).isEqualTo("active")
        assertThat(r.find(n4)!!.neighborId).isEqualTo(n4)
        assertThat(r.find(n1)!!.status).isEqualTo("closed")
    }

    // --- INV-MYAC-1: only their own account ---

    @Test
    fun `INV-MYAC-1 - given Ana and Ben registered, when asked for N1, then only Ana's account is returned`() {
        val r = registered()
        r.register(n2, "Ben Okafor", "ben@example.org")

        val account = r.find(n1)!!
        assertThat(account.neighborId).isEqualTo(n1)
        assertThat(account.name).isEqualTo("Ana Ruiz")
        assertThat(account.email).isEqualTo("ana@example.org")
    }

    @Test
    fun `INV-MYAC-1 - given an account that doesn't exist, when asked for N9, then not found`() {
        assertThat(registered().find(n9)).isNull()
    }

    @Test
    fun `INV-MYAC-1 INV-MYAC-7 - given N1 active, when Neighbor Suspended for N2, then N1 is unchanged`() {
        val r = registered()
        r.register(n2, "Ben Okafor", "ben@example.org")
        val before = r.find(n1)

        r.suspend(n2)

        assertThat(r.find(n1)).isEqualTo(before)
        assertThat(r.find(n2)!!.status).isEqualTo("suspended")
    }

    // --- INV-MYAC-10: idempotent ---

    @Test
    fun `INV-MYAC-10 - given N1 registered, when the same Neighbor Registered is applied again, then unchanged and still one account`() {
        val r = registered()
        r.changeEmail(n1, "ana.ruiz@example.net")
        val before = r.find(n1)

        r.register(n1, "Ana Ruiz", "ana@example.org")

        assertThat(r.find(n1)).isEqualTo(before)
    }

    @Test
    fun `INV-MYAC-10 - given any event applied twice, then the account is as after the first`() {
        val r = registered()
        r.changeEmail(n1, "a@example.net")
        r.changeEmail(n1, "a@example.net")
        r.suspend(n1)
        r.suspend(n1)
        assertThat(r.find(n1)!!.status).isEqualTo("suspended")
        r.reinstate(n1)
        r.reinstate(n1)
        assertThat(r.find(n1)!!.status).isEqualTo("active")
        r.close(n1)
        r.close(n1)
        assertThat(
            r.find(n1),
        ).isEqualTo(MyAccount(n1, "Ana Ruiz", "a@example.net", "closed", canChangeEmail = false, canCloseAccount = false))
    }
}
