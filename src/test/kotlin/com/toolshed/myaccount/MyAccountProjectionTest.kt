package com.toolshed.myaccount

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.placehold.HoldPlaced
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import com.toolshed.web.Caller
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * One test per scenario in slices/my-account.md, driving [MyAccountProjection] and [GetMyAccountQueryHandler]
 * directly against a fresh [InMemoryMyAccountRepository]. Together they cover every INV-MYAC id in the doc.
 */
class MyAccountProjectionTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val n1 = NeighborId(uuid("n1"))
    private val n2 = NeighborId(uuid("n2"))
    private val n4 = NeighborId(uuid("n4"))
    private val n9 = NeighborId(uuid("n9"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private val repository = InMemoryMyAccountRepository()
    private val projection = MyAccountProjection(repository)
    private val queries = GetMyAccountQueryHandler(repository)

    private fun ask(id: NeighborId): MyAccountRow? = queries.handle(GetMyAccount(id)).singleOrNull()

    private fun registerAna(id: NeighborId = n1) = projection.on(NeighborRegistered(id, "Ana Ruiz", "ana@example.org", now))

    private fun suspend(id: NeighborId = n1) = projection.on(NeighborSuspended(id, now))

    private fun close(id: NeighborId = n1) = projection.on(NeighborAccountClosed(id, false, now))

    @Test
    fun `INV-MYAC-2 INV-MYAC-3 INV-MYAC-9 INV-MYAC-11 given no account, when Neighbor Registered, then active with both actions offered`() {
        assertThat(ask(n1)).isNull()
        registerAna()
        val row = ask(n1)!!
        assertThat(row.name).isEqualTo("Ana Ruiz")
        assertThat(row.email).isEqualTo("ana@example.org")
        assertThat(row.status).isEqualTo(AccountStatus.ACTIVE)
        assertThat(row.canChangeEmail).isTrue()
        assertThat(row.canCloseAccount).isTrue()
    }

    @Test
    fun `INV-MYAC-2 given active account, when Neighbor Email Changed, then shows the new email and the same name`() {
        registerAna()
        projection.on(NeighborEmailChanged(n1, "ana.ruiz@example.net", now))
        val row = ask(n1)!!
        assertThat(row.name).isEqualTo("Ana Ruiz")
        assertThat(row.email).isEqualTo("ana.ruiz@example.net")
        assertThat(row.status).isEqualTo(AccountStatus.ACTIVE)
    }

    @Test
    fun `INV-MYAC-4 INV-MYAC-9 INV-MYAC-11 given active, when Neighbor Suspended, then suspended and both actions still offered`() {
        registerAna()
        suspend()
        val row = ask(n1)!!
        assertThat(row.status).isEqualTo(AccountStatus.SUSPENDED)
        assertThat(row.canChangeEmail).isTrue()
        assertThat(row.canCloseAccount).isTrue()
    }

    @Test
    fun `INV-MYAC-4 given suspended, when Neighbor Reinstated, then active`() {
        registerAna()
        suspend()
        projection.on(NeighborReinstated(n1, now))
        assertThat(ask(n1)!!.status).isEqualTo(AccountStatus.ACTIVE)
    }

    @Test
    fun `INV-MYAC-2 INV-MYAC-4 given suspended then reinstated, when Neighbor Email Changed, then new email and active`() {
        registerAna()
        suspend()
        projection.on(NeighborReinstated(n1, now))
        projection.on(NeighborEmailChanged(n1, "ana.ruiz@example.net", now))
        val row = ask(n1)!!
        assertThat(row.email).isEqualTo("ana.ruiz@example.net")
        assertThat(row.status).isEqualTo(AccountStatus.ACTIVE)
    }

    @Test
    fun `INV-MYAC-5 INV-MYAC-6 given active, when Neighbor Account Closed, then closed with neither action offered but still readable`() {
        registerAna()
        close()
        val row = ask(n1)!!
        assertThat(row.status).isEqualTo(AccountStatus.CLOSED)
        assertThat(row.canChangeEmail).isFalse()
        assertThat(row.canCloseAccount).isFalse()
        assertThat(row.name).isEqualTo("Ana Ruiz")
    }

    @Test
    fun `INV-MYAC-5 given suspended, when Neighbor Account Closed, then closed not suspended`() {
        registerAna()
        suspend()
        projection.on(NeighborAccountClosed(n1, true, now))
        assertThat(ask(n1)!!.status).isEqualTo(AccountStatus.CLOSED)
    }

    @Test
    fun `INV-MYAC-5 given closed, when a replayed suspend, reinstate or email change arrives, then it stays closed and unchanged`() {
        registerAna()
        close()
        suspend()
        projection.on(NeighborReinstated(n1, now))
        projection.on(NeighborEmailChanged(n1, "other@example.org", now))
        val row = ask(n1)!!
        assertThat(row.status).isEqualTo(AccountStatus.CLOSED)
        assertThat(row.email).isEqualTo("ana@example.org")
    }

    @Test
    fun `INV-MYAC-8 given N1 closed, when Neighbor Registered for new account N4, then N4 shows active and N1 still closed`() {
        registerAna()
        close()
        registerAna(n4)
        val fresh = ask(n4)!!
        assertThat(fresh.neighborId).isEqualTo(n4)
        assertThat(fresh.name).isEqualTo("Ana Ruiz")
        assertThat(fresh.email).isEqualTo("ana@example.org")
        assertThat(fresh.status).isEqualTo(AccountStatus.ACTIVE)
        assertThat(ask(n1)!!.status).isEqualTo(AccountStatus.CLOSED)
    }

    @Test
    fun `INV-MYAC-1 given N1 Ana and N2 Ben registered, when asked for N1, then only Ana's account is returned`() {
        registerAna()
        projection.on(NeighborRegistered(n2, "Ben Ito", "ben@example.org", now))
        val rows = queries.handle(GetMyAccount(n1))
        assertThat(rows).hasSize(1)
        assertThat(rows.single().neighborId).isEqualTo(n1)
        assertThat(rows.single().name).isEqualTo("Ana Ruiz")
    }

    @Test
    fun `INV-MYAC-1 INV-MYAC-7 given N1 active, when Neighbor Suspended for N2, then N1 is unchanged`() {
        registerAna()
        projection.on(NeighborRegistered(n2, "Ben Ito", "ben@example.org", now))
        val before = ask(n1)
        suspend(n2)
        assertThat(ask(n1)).isEqualTo(before)
        assertThat(ask(n2)!!.status).isEqualTo(AccountStatus.SUSPENDED)
    }

    @Test
    fun `INV-MYAC-7 given N1 active, when a loan and a hold are recorded for N1, then My Account is unchanged`() {
        // The projection has no handler for these events (MyAccountProjectionFixtureTest feeds them through the
        // real processor); here we only prove the stored account is untouched by the other slices' types.
        registerAna()
        val before = ask(n1)
        val handlers =
            MyAccountProjection::class.java.declaredMethods
                .filter { it.name == "on" }
                .map { it.parameterTypes.single() }
        assertThat(handlers).doesNotContain(ToolCheckedOut::class.java, HoldPlaced::class.java)
        assertThat(handlers).hasSize(5)
        assertThat(ask(n1)).isEqualTo(before)
        // Constructing the events keeps the scenario's wording honest.
        ToolCheckedOut(LoanId(uuid("l1")), ToolId(uuid("t1")), n1, 7, LocalDate.of(2026, 10, 13), now)
        HoldPlaced(HoldId(uuid("h1")), ToolId(uuid("t1")), n1, now)
    }

    @Test
    fun `INV-MYAC-10 given N1 active, when the same Neighbor Registered is applied again, then unchanged and one account`() {
        registerAna()
        projection.on(NeighborEmailChanged(n1, "ana.ruiz@example.net", now))
        val before = ask(n1)
        registerAna()
        assertThat(ask(n1)).isEqualTo(before)
        assertThat(queries.handle(GetMyAccount(n1))).hasSize(1)
    }

    @Test
    fun `INV-MYAC-10 given suspended then closed, when each event is applied twice, then the account is as after once`() {
        registerAna()
        suspend()
        suspend()
        assertThat(ask(n1)!!.status).isEqualTo(AccountStatus.SUSPENDED)
        close()
        close()
        assertThat(ask(n1)!!.status).isEqualTo(AccountStatus.CLOSED)
    }

    @Test
    fun `INV-MYAC-10 given email changed twice, when the first Neighbor Email Changed is redelivered, then still the latest email`() {
        registerAna()
        projection.on(NeighborEmailChanged(n1, "b@example.org", now.plusSeconds(10)))
        projection.on(NeighborEmailChanged(n1, "c@example.org", now.plusSeconds(20)))
        projection.on(NeighborEmailChanged(n1, "b@example.org", now.plusSeconds(10)))
        assertThat(ask(n1)!!.email).isEqualTo("c@example.org")
    }

    @Test
    fun `INV-MYAC-10 given an email changed, when the same Neighbor Email Changed is applied twice, then as after once`() {
        registerAna()
        projection.on(NeighborEmailChanged(n1, "b@example.org", now.plusSeconds(10)))
        val once = ask(n1)
        projection.on(NeighborEmailChanged(n1, "b@example.org", now.plusSeconds(10)))
        assertThat(ask(n1)).isEqualTo(once)
        assertThat(once!!.email).isEqualTo("b@example.org")
    }

    @Test
    fun `INV-MYAC-10 given suspended then reinstated, when the same Neighbor Suspended is redelivered, then still active`() {
        registerAna()
        projection.on(NeighborSuspended(n1, now))
        projection.on(NeighborReinstated(n1, now.plusSeconds(60)))
        projection.on(NeighborSuspended(n1, now))
        assertThat(ask(n1)!!.status).isEqualTo(AccountStatus.ACTIVE)
    }

    @Test
    fun `INV-MYAC-10 given reinstated then suspended again, when the same Neighbor Reinstated is redelivered, then still suspended`() {
        registerAna()
        projection.on(NeighborSuspended(n1, now))
        projection.on(NeighborReinstated(n1, now.plusSeconds(60)))
        projection.on(NeighborSuspended(n1, now.plusSeconds(120)))
        projection.on(NeighborReinstated(n1, now.plusSeconds(60)))
        assertThat(ask(n1)!!.status).isEqualTo(AccountStatus.SUSPENDED)
    }

    @Test
    fun `INV-MYAC-1 given no account N9, when asked for N9, then not found`() {
        assertThat(queries.handle(GetMyAccount(n9))).isEmpty()
    }

    @Test
    fun `INV-MYAC-13 given nobody is signed in, when My Account is asked for, then refused not-signed-in`() {
        val refused = assertThrows<CommandRejected> { with(Caller.ANONYMOUS) { requireAccountOwner(GetMyAccount(n1)) } }
        assertThat(refused.invariant).isEqualTo("INV-MYAC-13")
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }

    @Test
    fun `INV-MYAC-14 given Ben N2 is signed in, when Ana N1's account is asked for, then refused requester-mismatch`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller(neighborId = n2, isShedManager = false)) { requireAccountOwner(GetMyAccount(n1)) }
            }
        assertThat(refused.invariant).isEqualTo("INV-MYAC-14")
        assertThat(refused.reason).isEqualTo("requester-mismatch")
        assertThat(refused.message).isEqualTo("You can only see your own account.")
    }

    @Test
    fun `INV-MYAC-13 INV-MYAC-14 given the signed-in neighbor asks for their own account, then no refusal`() {
        with(Caller(neighborId = n1, isShedManager = false)) { requireAccountOwner(GetMyAccount(n1)) }
    }
}
