package com.toolshed.toolinventory

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.placehold.HoldPlaced
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
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** The Tool Inventory state view through the query bus: events in through the fixture, the list query answers. */
class ToolInventoryProjectionFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val zone = ZoneId.of("America/Denver")
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val ladder = ToolId(UUID.nameUUIDFromBytes("ladder".toByteArray()))
    private val ana = NeighborId(UUID.nameUUIDFromBytes("ana".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("ben".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val oct12 = LocalDate.of(2026, 10, 12)

    private var fixture: AxonTestFixture? = null

    private fun fixtureAt(instant: Instant): AxonTestFixture =
        AxonTestFixture
            .with(ToolInventoryConfiguration.configure(EventSourcingConfigurer.create(), Clock.fixed(instant, zone)))
            .also { fixture = it }

    @AfterEach
    fun afterEach() {
        fixture?.stop()
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
    fun `INV-TINV-1 INV-TINV-5 INV-TINV-6 the query shows a checked-out drill on loan to Ana with the extended due date`() {
        fixtureAt(now)
            .given()
            .recorded(
                listOf(
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, oct12, now),
                    LoanExtended(l1, oct12, oct12.plusDays(7), now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.any { row -> row.dueDate == oct12.plusDays(7) } }
                val row = inventory.tools.single()
                assertThat(row.borrowerName).isEqualTo("Ana Ruiz")
                assertThat(row.canMarkLost).isTrue()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-2 the query drops a retired tool once it is returned`() {
        fixtureAt(now)
            .given()
            .recorded(
                listOf(
                    NeighborRegistered(ben, "Ben", "ben@example.org", now),
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, ladder, ben, 3, oct12, now),
                    ToolRetired(ladder, now),
                    ToolReturned(l1, ladder, now, oct12, false),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.size == 1 }
                assertThat(inventory.tools.single().toolId).isEqualTo(drill)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-3 holds and overdue notices leave the drill's entry unchanged`() {
        fixtureAt(now)
            .given()
            .recorded(
                listOf(
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, oct12, now),
                    HoldPlaced(HoldId(UUID.nameUUIDFromBytes("h1".toByteArray())), drill, ben, now),
                    OverdueNoticeSent(l1, "ana@example.org", "Ana Ruiz", "Cordless drill", oct12, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.any { row -> row.onLoan } }
                val row = inventory.tools.single()
                assertThat(row.dueDate).isEqualTo(oct12)
                assertThat(row.borrowerName).isEqualTo("Ana Ruiz")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-9 the same loan is not overdue at 23_59 on its due date and overdue at 00_00 the next day, shed time`() {
        val events =
            listOf(
                NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                ToolAdded(drill, "Cordless drill", null, 7, now),
                ToolCheckedOut(l1, drill, ana, 7, oct12, now),
            )
        val lastMinute = oct12.atTime(23, 59).atZone(zone).toInstant()
        val midnight = oct12.plusDays(1).atStartOfDay(zone).toInstant()
        fixtureAt(lastMinute)
            .given()
            .recorded(events)
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                assertThat(queryUntil(gateway) { it.tools.any { row -> row.onLoan } }.tools.single().overdue).isFalse()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
        fixture?.stop()
        fixtureAt(midnight)
            .given()
            .recorded(events)
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                assertThat(queryUntil(gateway) { it.tools.any { row -> row.onLoan } }.tools.single().overdue).isTrue()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }
}
