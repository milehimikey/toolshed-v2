package com.toolshed.toolinventory

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
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
class ToolInventoryQueryFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val ladder = ToolId(UUID.nameUUIDFromBytes("ladder".toByteArray()))
    private val ana = NeighborId(UUID.nameUUIDFromBytes("ana".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
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
    fun `INV-TINV-1 INV-TINV-5 the query lists a checked-out tool with its borrower and due date`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.any { row -> row.toolId == drill && row.onLoan } }
                val row = inventory.tools.single { it.toolId == drill }
                assertThat(row.borrowerName).isEqualTo("Ana Ruiz")
                assertThat(row.dueDate).isEqualTo(LocalDate.of(2026, 10, 12))
                assertThat(row.canMarkLost).isTrue()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-2 a tool retired in the shed is not in the query answer`() {
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
                val inventory = queryUntil(gateway) { it.tools.any { row -> row.toolId == drill } }
                assertThat(inventory.tools.map { it.toolId }).containsExactly(drill)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-2 a tool retired while out drops off when it is returned`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                    ToolCheckedOut(l1, ladder, ana, 3, LocalDate.of(2026, 10, 12), now),
                    ToolRetired(ladder, now),
                    ToolReturned(l1, ladder, now, LocalDate.of(2026, 10, 11), false),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.none { row -> row.toolId == ladder } }
                assertThat(inventory.tools).isEmpty()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TINV-2 a tool recorded lost drops off the query answer`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                    ToolCheckedOut(l1, ladder, ana, 3, LocalDate.of(2026, 10, 12), now),
                    ToolLost(l1, ladder, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val inventory = queryUntil(gateway) { it.tools.none { row -> row.toolId == ladder } }
                assertThat(inventory.tools).isEmpty()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }
}
