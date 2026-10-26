package com.toolshed.myholds

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.placehold.HoldPlaced
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * The slice's own wiring (axon-projection-testing): built from [MyHoldsConfiguration.configure], so this proves
 * the projection really is registered on its processor and the query really is answered through the query bus,
 * not just that the plain classes behave correctly in isolation (covered by [MyHoldsScenarioTest]).
 */
class MyHoldsFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.nameUUIDFromBytes("t1".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("n2".toByteArray()))
    private val h1 = HoldId(UUID.nameUUIDFromBytes("h1".toByteArray()))

    private lateinit var repository: InMemoryMyHoldsRepository
    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        repository = InMemoryMyHoldsRepository()
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val configurer = MyHoldsConfiguration.configure(EventSourcingConfigurer.create(), repository, clock)
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    @Test
    fun `given a hold placed, when listed through the query gateway, then returns that hold`() {
        fixture
            .given()
            .events(ToolAdded(drill, "Cordless drill", null, 7, now), HoldPlaced(h1, drill, ben, now))
            .then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val rows = queryGateway.queryMany(ListMyHolds(ben), MyHoldsRow::class.java).join()
                    assertThat(rows).hasSize(1)
                    assertThat(rows.single().toolName).isEqualTo("Cordless drill")
                    assertThat(rows.single().position).isEqualTo(1)
                }
            }
    }

    @Test
    fun `given a hold placed then cancelled, when listed through the query gateway, then returns nothing`() {
        fixture
            .given()
            .events(
                ToolAdded(drill, "Cordless drill", null, 7, now),
                HoldPlaced(h1, drill, ben, now),
                HoldCancelled(h1, now),
            ).then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                // Waits for the tool name to be processed first, so an empty result reflects a cancelled hold
                // and not a processor that simply hasn't caught up yet.
                await().atMost(Duration.ofSeconds(5)).until { repository.toolName(drill) != null }
                val rows = queryGateway.queryMany(ListMyHolds(ben), MyHoldsRow::class.java).join()
                assertThat(rows).isEmpty()
            }
    }
}
