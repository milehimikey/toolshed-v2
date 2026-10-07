package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
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

/**
 * Scenario tests for the Neighbors view through the query bus, built from the slice's configure function. Each scenario
 * publishes the doc's Given as events, then asks the list query and checks the rows the doc's Then describes.
 */
class NeighborsQueryBusTest {
    private val zone = ZoneId.of("America/Denver")
    private val today = LocalDate.of(2026, 10, 1)
    private val t1: Instant = today.atTime(9, 0).atZone(zone).toInstant()
    private val t2: Instant =
        LocalDate
            .of(2026, 10, 2)
            .atTime(9, 0)
            .atZone(zone)
            .toInstant()
    private val t5: Instant =
        LocalDate
            .of(2026, 10, 5)
            .atTime(9, 0)
            .atZone(zone)
            .toInstant()

    private lateinit var config: AxonConfiguration
    private lateinit var events: EventGateway
    private lateinit var queries: QueryGateway
    private lateinit var units: UnitOfWorkFactory

    private val ana = NeighborId(UUID.randomUUID())
    private val ben = NeighborId(UUID.randomUUID())
    private val cara = NeighborId(UUID.randomUUID())

    @BeforeEach
    fun setUp() {
        val configurer =
            NeighborsConfiguration
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

    private fun list(): List<NeighborRow> = queries.query(ListNeighbors, NeighborList::class.java).join().rows

    private fun row(neighbor: NeighborId) = list().single { it.neighborId == neighbor }

    @Test
    fun `INV-NBRS-1 INV-NBRS-2 INV-NBRS-5 INV-NBRS-6 - given no neighbors, when Ana registers, then Ana is listed active with Suspend`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        val ana = list().single()
        assertEquals("Ana Ruiz", ana.name)
        assertEquals("ana@example.org", ana.email)
        assertEquals("active", ana.status)
        assertEquals(true, ana.canSuspend)
    }

    @Test
    fun `INV-NBRS-1 - given Ana Ruiz is listed, when a different account also named Ana Ruiz registers, then both are listed`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        given(NeighborRegistered(cara, "Ana Ruiz", "ana.r@example.net", t2))
        assertEquals(2, list().size)
    }

    @Test
    fun `INV-NBRS-7 - given Ana is listed once, when the same registration is applied again, then listed once`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        assertEquals(1, list().size)
    }

    @Test
    fun `INV-NBRS-5 - given Ana Ruiz with ana@example dot org, when her email changes, then listed with the new email, still active`() {
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1))
        given(NeighborEmailChanged(ana, "ana.ruiz@example.net", t2))
        val entry = row(ana)
        assertEquals("ana.ruiz@example.net", entry.email)
        assertEquals("active", entry.status)
    }

    @Test
    fun `INV-NBRS-2 INV-NBRS-6 - given Ana and Ben active, when Ana is suspended, then Ana has no Suspend, Ben unchanged`() {
        given(NeighborRegistered(ana, "Ana", "ana@example.org", t1), NeighborRegistered(ben, "Ben", "ben@example.org", t1))
        given(NeighborSuspended(ana, t2))
        assertEquals("suspended", row(ana).status)
        assertEquals(false, row(ana).canSuspend)
        assertEquals("active", row(ben).status)
    }

    @Test
    fun `INV-NBRS-2 INV-NBRS-6 - given Ana is suspended, when she is reinstated, then active again with Suspend offered`() {
        given(NeighborRegistered(ana, "Ana", "ana@example.org", t1), NeighborSuspended(ana, t2))
        given(NeighborReinstated(ana, t5))
        assertEquals("active", row(ana).status)
        assertEquals(true, row(ana).canSuspend)
    }

    @Test
    fun `INV-NBRS-3 INV-NBRS-6 - given Ana is active, when her account closes, then still listed as closed with no Suspend`() {
        given(NeighborRegistered(ana, "Ana", "ana@example.org", t1))
        given(NeighborAccountClosed(ana, false, t2))
        assertEquals("closed", row(ana).status)
        assertEquals(false, row(ana).canSuspend)
    }

    @Test
    fun `INV-NBRS-4 - given Ana is suspended, when she closes her account, then listed as closed`() {
        given(NeighborRegistered(ana, "Ana", "ana@example.org", t1), NeighborSuspended(ana, t2))
        given(NeighborAccountClosed(ana, true, t5))
        assertEquals("closed", row(ana).status)
    }

    @Test
    fun `INV-NBRS-4 - given Ana is closed, when an earlier suspension and reinstatement are applied again, then still closed`() {
        given(NeighborRegistered(ana, "Ana", "ana@example.org", t1), NeighborAccountClosed(ana, false, t2))
        given(NeighborSuspended(ana, t2), NeighborReinstated(ana, t5))
        assertEquals("closed", row(ana).status)
    }

    @Test
    fun `INV-NBRS-1 INV-NBRS-3 - given Ana Ruiz closed, when she signs up again, then listed twice, closed and active`() {
        val newAna = NeighborId(UUID.randomUUID())
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1), NeighborAccountClosed(ana, false, t2))
        given(NeighborRegistered(newAna, "Ana Ruiz", "ana@example.org", t5))
        val rows = list()
        assertEquals(2, rows.size)
        assertEquals("closed", rows.single { it.neighborId == ana }.status)
        assertEquals("active", rows.single { it.neighborId == newAna }.status)
    }

    @Test
    fun `INV-NBRS-8 - given Ana closed then signed up again, Ben between, when listed, then Ana closed, Ana again, Ben`() {
        val newAna = NeighborId(UUID.randomUUID())
        given(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", t1), NeighborAccountClosed(ana, false, t2))
        given(NeighborRegistered(ben, "Ben Ito", "ben@example.org", t2))
        given(NeighborRegistered(newAna, "Ana Ruiz", "ana@example.org", t5))
        assertEquals(listOf(ana, newAna, ben), list().map { it.neighborId })
    }

    @Test
    fun `INV-NBRS-2 INV-NBRS-3 INV-NBRS-6 - given Ana, Ben, Cara in three states, when listed, only Ana offers Suspend`() {
        given(
            NeighborRegistered(ana, "Ana", "ana@example.org", t1),
            NeighborRegistered(ben, "Ben", "ben@example.org", t1),
            NeighborRegistered(cara, "Cara", "cara@example.org", t1),
            NeighborSuspended(ben, t2),
            NeighborAccountClosed(cara, false, t2),
        )
        val rows = list()
        assertEquals(3, rows.size)
        assertEquals(
            listOf(true, false, false),
            rows
                .map {
                    it.canSuspend
                }.let { listOf(row(ana).canSuspend, row(ben).canSuspend, row(cara).canSuspend) },
        )
        assertEquals(listOf("active", "suspended", "closed"), listOf(row(ana).status, row(ben).status, row(cara).status))
    }
}
