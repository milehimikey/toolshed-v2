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
import java.time.ZoneOffset
import java.util.UUID

/**
 * The Tool Inventory state view through the query bus: events go in through the fixture, the projection applies
 * them, and the list query answers. Each assertion waits until the view has caught up. The clock is fixed so
 * overdue is a known fact rather than a race against the real one.
 */
class ToolInventoryProjectionFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val ladder = ToolId(UUID.nameUUIDFromBytes("ladder".toByteArray()))
    private val ana = NeighborId(UUID.nameUUIDFromBytes("ana".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("ben".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val h1 = HoldId(UUID.nameUUIDFromBytes("h1".toByteArray()))
    private val oct12 = LocalDate.of(2026, 10, 12)

    private var fixture: AxonTestFixture? = null

    private fun start(today: LocalDate = LocalDate.of(2026, 10, 5)): AxonTestFixture {
        val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
        return AxonTestFixture.with(ToolInventoryConfiguration.configure(EventSourcingConfigurer.create(), clock)).also { fixture = it }
    }

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

    private fun run(
        today: LocalDate = LocalDate.of(2026, 10, 5),
        events: List<Any>,
        until: (ToolInventory) -> Boolean,
        assertion: (ToolInventory) -> Unit,
    ) {
        start(today)
            .given()
            .recorded(events)
            .execute { config -> assertion(queryUntil(config.getComponent(QueryGateway::class.java), until)) }
            .`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-1 INV-TINV-4 INV-TINV-7 the query lists an added tool in the shed with retire offered`() {
        run(
            events = listOf(ToolAdded(drill, "Cordless drill", "18V, two batteries", 7, now)),
            until = { it.tools.isNotEmpty() },
        ) { inventory ->
            val row = inventory.tools.single()
            assertThat(row.description).isEqualTo("18V, two batteries")
            assertThat(row.defaultCheckoutPeriodDays).isEqualTo(7)
            assertThat(row.onLoan).isFalse()
            assertThat(row.canRetire).isTrue()
            assertThat(row.canMarkLost).isFalse()
        }
    }

    @Test
    fun `INV-TINV-5 INV-TINV-6 the query shows a checked-out tool with the registered name, extended due date and mark lost`() {
        run(
            events =
                listOf(
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, oct12, now),
                    LoanExtended(l1, oct12, LocalDate.of(2026, 10, 19), now),
                ),
            until = { it.tools.any { row -> row.dueDate == LocalDate.of(2026, 10, 19) } },
        ) { inventory ->
            val row = inventory.tools.single()
            assertThat(row.onLoan).isTrue()
            assertThat(row.borrowerName).isEqualTo("Ana Ruiz")
            assertThat(row.canMarkLost).isTrue()
        }
    }

    @Test
    fun `INV-TINV-5 INV-TINV-6 the query shows a returned tool in the shed again`() {
        run(
            events =
                listOf(
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, oct12, now),
                    ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 6), false),
                ),
            until = { it.tools.any { row -> !row.onLoan } },
        ) { inventory ->
            val row = inventory.tools.single()
            assertThat(row.borrowerName).isNull()
            assertThat(row.dueDate).isNull()
            assertThat(row.canMarkLost).isFalse()
        }
    }

    @Test
    fun `INV-TINV-2 the query drops a tool retired in the shed and keeps the other`() {
        run(
            events =
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    ToolRetired(drill, now),
                ),
            until = { it.tools.size == 1 },
        ) { inventory -> assertThat(inventory.tools.single().toolId).isEqualTo(ladder) }
    }

    @Test
    fun `INV-TINV-2 INV-TINV-7 the query keeps a tool retired while out, marked retired, and drops it when recorded lost`() {
        run(
            events =
                listOf(
                    NeighborRegistered(ben, "Ben Cole", "ben@example.org", now),
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    ToolCheckedOut(l1, ladder, ben, 3, oct12, now),
                    ToolRetired(ladder, now),
                ),
            until = { it.tools.any { row -> row.retired } },
        ) { inventory ->
            val row = inventory.tools.single()
            assertThat(row.onLoan).isTrue()
            assertThat(row.canRetire).isFalse()
            assertThat(row.canMarkLost).isTrue()
        }
        fixture?.stop()
        run(
            events =
                listOf(
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, ladder, ben, 3, oct12, now),
                    ToolRetired(ladder, now),
                    ToolLost(l1, ladder, now),
                ),
            until = { it.tools.any { row -> row.toolId == drill } },
        ) { inventory -> assertThat(inventory.tools.map { it.toolId }).containsExactly(drill) }
    }

    @Test
    fun `INV-TINV-3 holds and notices leave the entry unchanged`() {
        run(
            events =
                listOf(
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, oct12, now),
                    HoldPlaced(h1, drill, ben, now),
                    ToolAdded(ladder, "Ladder", null, 3, now),
                ),
            until = { it.tools.size == 2 },
        ) { inventory ->
            val row = inventory.tools.single { it.toolId == drill }
            assertThat(row.onLoan).isTrue()
            assertThat(row.dueDate).isEqualTo(oct12)
            assertThat(row.borrowerName).isEqualTo("Ana Ruiz")
        }
    }

    @Test
    fun `INV-TINV-9 the query reports overdue only once today is after the due date`() {
        val events =
            listOf(
                NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                ToolAdded(drill, "Cordless drill", null, 7, now),
                ToolCheckedOut(l1, drill, ana, 7, oct12, now),
            )
        run(today = oct12, events = events, until = { it.tools.any { row -> row.onLoan } }) { inventory ->
            assertThat(inventory.tools.single().overdue).isFalse()
        }
        fixture?.stop()
        run(today = oct12.plusDays(1), events = events, until = { it.tools.any { row -> row.onLoan } }) { inventory ->
            assertThat(inventory.tools.single().overdue).isTrue()
        }
    }

    @Test
    fun `INV-TINV-10 the query lists by name ignoring case, then by when added`() {
        val second = ToolId(UUID.nameUUIDFromBytes("drill2".toByteArray()))
        run(
            events =
                listOf(
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolAdded(second, "cordless drill", null, 7, now),
                ),
            until = { it.tools.size == 3 },
        ) { inventory -> assertThat(inventory.tools.map { it.toolId }).containsExactly(drill, second, ladder) }
    }

    @Test
    fun `INV-TINV-8 a Tool Added applied twice lists the tool once`() {
        val added = ToolAdded(drill, "Cordless drill", null, 7, now)
        run(
            events = listOf(added, added, ToolAdded(ladder, "Ladder", null, 3, now)),
            until = { it.tools.size >= 2 },
        ) { inventory -> assertThat(inventory.tools.map { it.toolId }).containsExactly(drill, ladder) }
    }
}
