package com.toolshed.toolmaintenance

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.completetoolmaintenance.ToolMaintenanceCompleted
import com.toolshed.placehold.HoldPlaced
import com.toolshed.retiretool.ToolRetired
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceScheduled
import com.toolshed.scheduletoolmaintenance.ToolMaintenanceTasksChanged
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
import java.util.UUID

/** The Tool Maintenance state view through the query bus: events go in through the fixture, the list query answers. */
class ToolMaintenanceProjectionFixtureTest {
    private val nov3 = Instant.parse("2026-11-03T09:00:00Z")
    private val nov3at11 = Instant.parse("2026-11-03T11:00:00Z")
    private val trimmer = ToolId(UUID.nameUUIDFromBytes("trimmer".toByteArray()))
    private val ladder = ToolId(UUID.nameUUIDFromBytes("ladder".toByteArray()))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        fixture = AxonTestFixture.with(ToolMaintenanceConfiguration.configure(EventSourcingConfigurer.create()))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    private fun queryUntil(
        gateway: QueryGateway,
        check: (ToolMaintenance) -> Boolean,
    ): ToolMaintenance {
        var last: ToolMaintenance? = null
        Awaitility.await().atMost(Duration.ofSeconds(30)).until {
            last = gateway.query(ListToolsInMaintenance, ToolMaintenance::class.java).join()
            check(last!!)
        }
        return last!!
    }

    private fun scheduled(
        tool: ToolId,
        name: String,
        task: String,
    ) = ToolMaintenanceScheduled(tool, name, listOf(task), null, "Sam", nov3)

    @Test
    fun `INV-TMNT-1 INV-TMNT-11 the query lists a scheduled tool and shows the replaced tasks after a change`() {
        fixture
            .given()
            .recorded(
                listOf(
                    scheduled(trimmer, "Hedge trimmer", "sharpen blades"),
                    ToolMaintenanceTasksChanged(trimmer, listOf("replace cord"), "cord frayed", "Sam", nov3at11),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val view = queryUntil(gateway) { it.tools.singleOrNull()?.lastChangedAt != null }
                val row = view.tools.single()
                assertThat(row.toolName).isEqualTo("Hedge trimmer")
                assertThat(row.tasks).containsExactly("replace cord")
                assertThat(row.notes).isEqualTo("cord frayed")
                assertThat(row.lastChangedBy).isEqualTo("Sam")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TMNT-2 INV-TMNT-3 INV-TMNT-8 completed and retired tools drop off, the rest come back in order`() {
        val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
        fixture
            .given()
            .recorded(
                listOf(
                    scheduled(trimmer, "Hedge trimmer", "a"),
                    scheduled(ladder, "Ladder", "b"),
                    scheduled(drill, "cordless drill", "c"),
                    ToolMaintenanceCompleted(trimmer, nov3at11),
                    ToolRetired(ladder, nov3at11),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val view = queryUntil(gateway) { it.tools.map { row -> row.toolId } == listOf(drill) }
                assertThat(view.tools.single().tasks).containsExactly("c")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-TMNT-5 given trimmer listed when drill added and a hold placed then only the trimmer is listed`() {
        val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
        val hold = HoldId(UUID.nameUUIDFromBytes("hold".toByteArray()))
        val neighbor = NeighborId(UUID.nameUUIDFromBytes("neighbor".toByteArray()))
        fixture
            .given()
            .recorded(
                listOf(
                    scheduled(trimmer, "Hedge trimmer", "sharpen blades"),
                    ToolAdded(drill, "Cordless drill", null, 7, nov3),
                    HoldPlaced(hold, trimmer, neighbor, nov3at11),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                // Let the later events be delivered too, then the view must still list only the trimmer.
                Awaitility.await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(30)).untilAsserted {
                    val view = gateway.query(ListToolsInMaintenance, ToolMaintenance::class.java).join()
                    assertThat(view.tools.map { it.toolId }).containsExactly(trimmer)
                    assertThat(view.tools.single().tasks).containsExactly("sharpen blades")
                }
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }
}
