package com.toolshed.toolcatalog

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.completetoolmaintenance.ToolMaintenanceCompleted
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.retiretool.ToolRetired
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceScheduled
import com.toolshed.sendoverduenotice.OverdueNoticeSent
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.AxonTestPhase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The Tool Catalog state view through the query bus: events go in through the fixture, the projection applies
 * them, and the list query answers. Each assertion waits until the view has caught up.
 */
class ToolCatalogProjectionFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val ladder = ToolId(UUID.nameUUIDFromBytes("ladder".toByteArray()))
    private val ana = NeighborId(UUID.nameUUIDFromBytes("ana".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("ben".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val h1 = HoldId(UUID.nameUUIDFromBytes("h1".toByteArray()))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        fixture = AxonTestFixture.with(ToolCatalogConfiguration.configure(EventSourcingConfigurer.create()))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    // The marker is recorded after the scenario's events, so once the view lists it, every earlier event has been applied.
    private val marker = ToolId(UUID.nameUUIDFromBytes("marker".toByteArray()))

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given =
        (events + ToolAdded(marker, "~marker", null, 1, now.plusSeconds(3600))).fold(this) { phase, e -> phase.event(e) }

    /** Waits until the view has applied every recorded event (it lists the marker), then answers without the marker. */
    private fun settled(gateway: QueryGateway): ToolCatalog {
        val catalog = queryUntil(gateway) { it.tools.any { row -> row.toolId == marker } }
        return ToolCatalog(catalog.tools.filterNot { it.toolId == marker })
    }

    private fun queryUntil(
        gateway: QueryGateway,
        check: (ToolCatalog) -> Boolean,
    ): ToolCatalog {
        var last: ToolCatalog? = null
        Awaitility.await().atMost(Duration.ofSeconds(30)).until {
            last = gateway.query(ListToolCatalog, ToolCatalog::class.java).join()
            check(last!!)
        }
        return last!!
    }

    @Test
    fun `INV-TCAT-1 the query lists an added tool as available, with no holds waiting`() {
        fixture
            .given()
            .recorded(listOf(ToolAdded(drill, "Cordless drill", "18V, two batteries", 7, now)))
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val catalog = settled(gateway)
                val row = catalog.tools.single { it.toolId == drill }
                assertThat(row.description).isEqualTo("18V, two batteries")
                assertThat(row.defaultCheckoutPeriodDays).isEqualTo(7)
                assertThat(row.status).isEqualTo(ToolCatalogStatus.AVAILABLE)
                assertThat(row.holdsWaiting).isZero()
                assertThat(row.canCheckOut).isTrue()
                assertThat(row.canPlaceHold).isFalse()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TCAT-2 the query shows a checked-out tool with its due date, with no borrower in the answer`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val catalog = settled(gateway)
                val row = catalog.tools.single { it.toolId == drill }
                assertThat(row.dueDate).isEqualTo(LocalDate.of(2026, 10, 12))
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TCAT-4 a hold fulfilled while a second hold waits shows set aside with 1 hold waiting`() {
        val h2 = HoldId(UUID.nameUUIDFromBytes("h2".toByteArray()))
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    HoldPlaced(h1, drill, ben, now),
                    HoldPlaced(h2, drill, ana, now),
                    HoldFulfilled(h1, drill, ben, LocalDate.of(2026, 10, 9), now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val catalog = settled(gateway)
                val row = catalog.tools.single { it.toolId == drill }
                assertThat(row.holdsWaiting).isEqualTo(1)
                assertThat(row.canCheckOut).isTrue()
                assertThat(row.canPlaceHold).isTrue()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TCAT-6 a cancelled hold leaves the other waiting hold, the tool stays checked out`() {
        val h2 = HoldId(UUID.nameUUIDFromBytes("h2".toByteArray()))
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                    HoldPlaced(h1, drill, ben, now),
                    HoldPlaced(h2, drill, ana, now),
                    HoldCancelled(h2, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val catalog = settled(gateway)
                val row = catalog.tools.single { it.toolId == drill }
                assertThat(row.status).isEqualTo(ToolCatalogStatus.CHECKED_OUT)
                assertThat(row.holdsWaiting).isEqualTo(1)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TCAT-8 a tool retired in the shed is not in the query answer, the other tool stays`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolRetired(ladder, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val catalog = settled(gateway)
                assertThat(catalog.tools.map { it.toolId }).containsExactly(drill)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TCAT-9 an overdue notice sent leaves the entry unchanged`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val before = settled(gateway)
                assertThat(before.tools.single { it.toolId == drill }.dueDate).isEqualTo(LocalDate.of(2026, 10, 12))
            }.`when`()
            .event(
                OverdueNoticeSent(l1, "ana@example.org", "Ana Ruiz", "Cordless drill", LocalDate.of(2026, 10, 12), now),
            ).then()
            .success()
    }

    @Test
    fun `INV-TCAT-13 the query lists tools by name ignoring case, then by when they were added`() {
        val second = ToolId(UUID.nameUUIDFromBytes("drill-2".toByteArray()))
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    ToolAdded(drill, "cordless drill", null, 7, now.plusSeconds(1)),
                    ToolAdded(second, "Cordless Drill", null, 7, now.plusSeconds(2)),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val catalog = settled(gateway)
                assertThat(catalog.tools.map { it.toolId }).containsExactly(drill, second, ladder)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TCAT-15 maintenance scheduled hides the tool from the query answer`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolAdded(ladder, "Ladder", null, 3, now.plusSeconds(1)),
                    ToolMaintenanceScheduled(drill, "Cordless drill", listOf("Charge batteries"), null, "Mo", now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val catalog = settled(gateway)
                assertThat(catalog.tools.map { it.toolId }).containsExactly(ladder)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TCAT-16 maintenance completed lists the tool again as available through the query`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolMaintenanceScheduled(drill, "Cordless drill", listOf("Charge batteries"), null, "Mo", now),
                    ToolMaintenanceCompleted(drill, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val catalog = settled(gateway)
                assertThat(catalog.tools.single().status).isEqualTo(ToolCatalogStatus.AVAILABLE)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TCAT-6 Neighbor Account Closed removes only that neighbor's holds through the query`() {
        val h2 = HoldId(UUID.nameUUIDFromBytes("h2".toByteArray()))
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                    HoldPlaced(h1, drill, ben, now),
                    HoldPlaced(h2, drill, ana, now),
                    NeighborAccountClosed(ben, false, now),
                ),
            ).execute { config ->
                val catalog = settled(config.getComponent(QueryGateway::class.java))
                val row = catalog.tools.single { it.toolId == drill }
                assertThat(row.status).isEqualTo(ToolCatalogStatus.CHECKED_OUT)
                assertThat(row.holdsWaiting).isEqualTo(1)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TCAT-12 INV-TCAT-16 a maintenance scheduled redelivered after completion leaves the tool listed as available`() {
        val at10 = Instant.parse("2026-11-02T10:00:00Z")
        val at14 = Instant.parse("2026-11-02T14:00:00Z")
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolMaintenanceScheduled(drill, "Cordless drill", listOf("Charge batteries"), null, "Mo", at10),
                    ToolMaintenanceCompleted(drill, at14),
                    ToolMaintenanceScheduled(drill, "Cordless drill", listOf("Charge batteries"), null, "Mo", at10),
                ),
            ).execute { config ->
                val catalog = settled(config.getComponent(QueryGateway::class.java))
                assertThat(catalog.tools.single { it.toolId == drill }.status).isEqualTo(ToolCatalogStatus.AVAILABLE)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TCAT-15 a maintenance scheduled again after completion hides the tool through the query`() {
        val at10 = Instant.parse("2026-11-02T10:00:00Z")
        val at14 = Instant.parse("2026-11-02T14:00:00Z")
        val at16 = Instant.parse("2026-11-02T16:00:00Z")
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolMaintenanceScheduled(drill, "Cordless drill", listOf("Charge batteries"), null, "Mo", at10),
                    ToolMaintenanceCompleted(drill, at14),
                    ToolMaintenanceScheduled(drill, "Cordless drill", listOf("Sharpen bit"), null, "Mo", at16),
                ),
            ).execute { config ->
                assertThat(settled(config.getComponent(QueryGateway::class.java)).tools).isEmpty()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }
}
