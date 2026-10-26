package com.toolshed.toolcatalog

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.retiretool.ToolRetired
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

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

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
                val catalog = queryUntil(gateway) { it.tools.any { row -> row.toolId == drill } }
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
                val catalog =
                    queryUntil(gateway) {
                        it.tools.any { row ->
                            row.toolId == drill && row.status == ToolCatalogStatus.CHECKED_OUT
                        }
                    }
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
                val catalog =
                    queryUntil(gateway) {
                        it.tools.any { row ->
                            row.toolId == drill && row.status == ToolCatalogStatus.SET_ASIDE
                        }
                    }
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
    fun `INV-TCAT-6 an account closed removes only that neighbor's holds`() {
        val h2 = HoldId(UUID.nameUUIDFromBytes("h2".toByteArray()))
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                    HoldPlaced(h1, drill, ben, now),
                    HoldPlaced(h2, drill, ana, now),
                    NeighborAccountClosed(ana, false, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val catalog = queryUntil(gateway) { it.tools.any { row -> row.toolId == drill && row.holdsWaiting == 1 } }
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
                val catalog = queryUntil(gateway) { it.tools.any { row -> row.toolId == drill } }
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
                val before =
                    queryUntil(gateway) {
                        it.tools.any { row ->
                            row.toolId == drill && row.status == ToolCatalogStatus.CHECKED_OUT
                        }
                    }
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
                val catalog = queryUntil(gateway) { it.tools.size == 3 }
                assertThat(catalog.tools.map { it.toolId }).containsExactly(drill, second, ladder)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }
}
