package com.toolshed.myaccount

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
import io.axoniq.framework.axonserver.connector.configuration.AxonServerConfigurationEnhancer
import org.axonframework.common.configuration.AxonConfiguration
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.gateway.EventGateway
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Scenario tests for My Account through the query bus, built from the slice's configure function. */
class MyAccountQueryBusTest {
    private val zone = ZoneId.of("America/Denver")
    private val t1: Instant =
        LocalDate
            .of(2026, 10, 1)
            .atTime(9, 0)
            .atZone(zone)
            .toInstant()
    private val t2: Instant =
        LocalDate
            .of(2026, 10, 2)
            .atTime(9, 0)
            .atZone(zone)
            .toInstant()

    private lateinit var config: AxonConfiguration
    private lateinit var events: EventGateway
    private lateinit var queries: QueryGateway
    private lateinit var units: UnitOfWorkFactory

    private val ana = NeighborId(UUID.randomUUID())
    private val ben = NeighborId(UUID.randomUUID())

    @BeforeEach
    fun setUp() {
        val configurer =
            MyAccountConfiguration
                .configure(EventSourcingConfigurer.create())
                .componentRegistry { cr -> cr.disableEnhancer(AxonServerConfigurationEnhancer::class.java) }
        config = configurer.start()
        events = config.getComponent(EventGateway::class.java)
        queries = config.getComponent(QueryGateway::class.java)
        units = config.getComponent(UnitOfWorkFactory::class.java)
    }

    @AfterEach
    fun tearDown() {
        config.shutdown()
    }

    /** Publishes the Given events for one scenario, in order. */
    private fun given(vararg recorded: Any) {
        val uow = units.create()
        uow.onInvocation { ctx -> events.publish(ctx, recorded.toList()) }
        uow.execute().join()
    }

    private fun lookup(neighbor: NeighborId): MyAccountRow? =
        queries.query(GetMyAccount(neighbor), MyAccountLookup::class.java).join().account

    private fun mine(neighbor: NeighborId): MyAccountRow = lookup(neighbor)!!

    @Test
    fun `INV-MYAC-2 INV-MYAC-3 INV-MYAC-9 INV-MYAC-11 - given no account, when Ana registers, then active with both actions`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        val row = mine(ana)
        assertEquals("Ana Ruiz", row.name)
        assertEquals("active", row.status)
        assertTrue(row.canChangeEmail && row.canCloseAccount)
    }

    @Test
    fun `INV-MYAC-2 - given Ana is active, when her email changes, then the new email shows and she stays active`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        given(NeighborEmailChanged(ana, "ana.ruiz@example.net", t2))
        assertEquals("ana.ruiz@example.net", mine(ana).email)
        assertEquals("active", mine(ana).status)
    }

    @Test
    fun `INV-MYAC-4 INV-MYAC-9 INV-MYAC-11 - given Ana is active, when suspended, then suspended with both actions still offered`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        given(NeighborSuspended(ana, t2))
        assertEquals("suspended", mine(ana).status)
        assertTrue(mine(ana).canChangeEmail && mine(ana).canCloseAccount)
    }

    @Test
    fun `INV-MYAC-4 - given Ana is suspended, when reinstated, then active`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1), NeighborSuspended(ana, t2))
        given(NeighborReinstated(ana, t2))
        assertEquals("active", mine(ana).status)
    }

    @Test
    fun `INV-MYAC-2 INV-MYAC-4 - given Ana was reinstated, when her email changes, then the new email shows, active`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1), NeighborSuspended(ana, t2))
        given(NeighborReinstated(ana, t2))
        given(NeighborEmailChanged(ana, "ana.ruiz@example.net", t2))
        assertEquals("ana.ruiz@example.net", mine(ana).email)
        assertEquals("active", mine(ana).status)
    }

    @Test
    fun `INV-MYAC-5 INV-MYAC-6 - given Ana is active, when closed, then closed with neither action and still readable`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        given(NeighborAccountClosed(ana, false, t2))
        val row = mine(ana)
        assertEquals("closed", row.status)
        assertTrue(!row.canChangeEmail && !row.canCloseAccount)
    }

    @Test
    fun `INV-MYAC-5 - given Ana is suspended, when she closes, then closed and not suspended`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1), NeighborSuspended(ana, t2))
        given(NeighborAccountClosed(ana, true, t2))
        assertEquals("closed", mine(ana).status)
    }

    @Test
    fun `INV-MYAC-8 - given Ana closed, when she signs up again as N4, then N4 is active and Ana's old account is not N4`() {
        val n4 = NeighborId(UUID.randomUUID())
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1), NeighborAccountClosed(ana, false, t2))
        given(NeighborRegistered(n4, "Ana Ruiz", "ana@example.org", t2))
        assertEquals("active", mine(n4).status)
        assertEquals("closed", mine(ana).status)
        assertEquals(n4, mine(n4).neighborId)
    }

    @Test
    fun `INV-MYAC-1 - given Ana and Ben registered, when Ana is asked for, then only Ana's account is returned`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1), NeighborRegistered(ben, "Ben Ito", "ben@example.org", t1))
        assertEquals(ana, mine(ana).neighborId)
        assertEquals("Ana Ruiz", mine(ana).name)
    }

    @Test
    fun `INV-MYAC-1 INV-MYAC-7 - given Ana is active, when Ben is suspended, then Ana is unchanged`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1), NeighborRegistered(ben, "Ben Ito", "ben@example.org", t1))
        given(NeighborSuspended(ben, t2))
        assertEquals("active", mine(ana).status)
    }

    @Test
    fun `INV-MYAC-7 - given Ana is active, when a tool is checked out to her and a hold placed, then she is unchanged`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        given(ToolCheckedOut(LoanId(UUID.randomUUID()), ToolId(UUID.randomUUID()), ana, 7, LocalDate.of(2026, 10, 8), t2))
        given(HoldPlaced(HoldId(UUID.randomUUID()), ToolId(UUID.randomUUID()), ana, t2))
        assertEquals("active", mine(ana).status)
        assertEquals("Ana Ruiz", mine(ana).name)
    }

    @Test
    fun `INV-MYAC-10 - given Ana is active, when the same registration is applied again, then unchanged and still one account`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        assertEquals("active", mine(ana).status)
    }

    @Test
    fun `INV-MYAC-1 - given no account N9, when asked for, then not found`() {
        assertNull(lookup(NeighborId(UUID.randomUUID())))
    }
}
