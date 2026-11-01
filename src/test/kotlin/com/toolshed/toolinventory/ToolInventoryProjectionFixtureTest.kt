package com.toolshed.toolinventory

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * The Tool Inventory state view through the query bus: events go in through the fixture, the projection applies
 * them, and the list query answers. Each assertion waits until the view has caught up.
 */
class ToolInventoryProjectionFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val ladder = ToolId(UUID.nameUUIDFromBytes("ladder".toByteArray()))
    private val ana = NeighborId(UUID.nameUUIDFromBytes("ana".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("ben".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val l2 = LoanId(UUID.nameUUIDFromBytes("l2".toByteArray()))
    private val oct12 = LocalDate.of(2026, 10, 12)
    private val oct20 = LocalDate.of(2026, 10, 20)

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        // Today is 13 October in the shed's zone, so a loan due 12 October is overdue and one due 20 October is not.
        val clock = Clock.fixed(oct12.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
        fixture = AxonTestFixture.with(ToolInventoryConfiguration.configure(EventSourcingConfigurer.create(), clock))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    private fun queryUntil(
        gateway: QueryGateway,
        check: (ToolInventory) -> Boolean,
    ): ToolInventory {
        var last: ToolInventory? = null
        Awaitility.await().atMost(Duration.ofSeconds(30)).until {
            last = gateway.query(ListToolInventory, ToolInventory::class.java).join()
            check(last!!)
        }
        return last!!
    }

    @Test
    fun `INV-TINV-1 INV-TINV-4 INV-TINV-7 the query lists an added tool in the shed, retire offered, no mark lost`() {
        fixture
            .given()
            .recorded(listOf(ToolAdded(drill, "Cordless drill", "18V, two batteries", 7, now)))
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.any { row -> row.toolId == drill } }
                val row = inventory.tools.single { it.toolId == drill }
                assertThat(row.description).isEqualTo("18V, two batteries")
                assertThat(row.defaultCheckoutPeriodDays).isEqualTo(7)
                assertThat(row.onLoan).isFalse()
                assertThat(row.canRetire).isTrue()
                assertThat(row.canMarkLost).isFalse()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-5 INV-TINV-6 INV-TINV-9 the query shows an out tool with the borrower's name, due date, overdue and mark lost`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                    ToolCheckedOut(l1, drill, ana, 7, oct12, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.any { row -> row.onLoan } }
                val row = inventory.tools.single { it.toolId == drill }
                assertThat(row.borrowerName).isEqualTo("Ana Ruiz")
                assertThat(row.dueDate).isEqualTo(oct12)
                assertThat(row.overdue).isTrue()
                assertThat(row.canMarkLost).isTrue()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-5 the query shows the extended due date`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                    ToolCheckedOut(l1, drill, ana, 7, oct12, now),
                    LoanExtended(l1, oct12, oct20, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.any { row -> row.dueDate == oct20 } }
                assertThat(inventory.tools.single().overdue).isFalse()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-5 INV-TINV-6 the query shows no borrower, due date or mark lost once the tool is returned`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                    ToolCheckedOut(l1, drill, ana, 7, oct12, now),
                    ToolReturned(l1, drill, now, oct12, false),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.isNotEmpty() && it.tools.none { row -> row.onLoan } }
                val row = inventory.tools.single()
                assertThat(row.borrowerName).isNull()
                assertThat(row.dueDate).isNull()
                assertThat(row.canMarkLost).isFalse()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-2 INV-TINV-6 INV-TINV-7 a tool retired while out stays listed, retired and out, then drops off when lost`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    NeighborRegistered(ben, "Ben Okafor", "ben@example.org", now),
                    ToolCheckedOut(l2, ladder, ben, 7, oct20, now),
                    ToolRetired(drill, now),
                    ToolRetired(ladder, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.size == 1 && it.tools.single().retired }
                val row = inventory.tools.single()
                assertThat(row.toolId).isEqualTo(ladder)
                assertThat(row.onLoan).isTrue()
                assertThat(row.borrowerName).isEqualTo("Ben Okafor")
                assertThat(row.canMarkLost).isTrue()
                assertThat(row.canRetire).isFalse()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-2 a retired tool out on loan drops off the query once it is recorded lost`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    ToolAdded(drill, "Cordless drill", null, 7, now), // a marker: it lists only after the events before it applied
                    NeighborRegistered(ben, "Ben Okafor", "ben@example.org", now),
                    ToolCheckedOut(l2, ladder, ben, 7, oct20, now),
                    ToolRetired(ladder, now),
                    ToolLost(l2, ladder, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.none { row -> row.toolId == ladder } && it.tools.isNotEmpty() }
                assertThat(inventory.tools.map { it.toolId }).containsExactly(drill)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-10 INV-TINV-1 the query lists in name order, ignoring case, then by when added, no paging`() {
        val drill2 = ToolId(UUID.nameUUIDFromBytes("drill2".toByteArray()))
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolAdded(drill2, "Cordless drill", null, 7, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.size == 3 }
                assertThat(inventory.tools.map { it.toolId }).containsExactly(drill, drill2, ladder)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-3 holds and overdue notices leave the entry unchanged`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                    ToolCheckedOut(l1, drill, ana, 7, oct12, now),
                    HoldPlaced(HoldId(UUID.nameUUIDFromBytes("h1".toByteArray())), drill, ben, now),
                    OverdueNoticeSent(l1, "ana@example.org", "Ana Ruiz", "Cordless drill", oct12, now),
                    ToolAdded(ladder, "Ladder", null, 3, now), // a marker: once it lists, the events before it were applied
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.size == 2 }
                val row = inventory.tools.single { it.toolId == drill }
                assertThat(row.onLoan).isTrue()
                assertThat(row.borrowerName).isEqualTo("Ana Ruiz")
                assertThat(row.dueDate).isEqualTo(oct12)
                assertThat(row.retired).isFalse()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }
}
