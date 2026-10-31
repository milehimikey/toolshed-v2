package com.toolshed.holdstofulfill

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.placehold.HoldPlaced
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Built from [HoldsToFulfillConfiguration.configure]: proves the projection is registered on its processor and the
 * list is answered through the query bus (INV-HTF-1, INV-HTF-2).
 */
class HoldsToFulfillProjectionFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.nameUUIDFromBytes("t1".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("n2".toByteArray()))
    private val cleo = NeighborId(UUID.nameUUIDFromBytes("n3".toByteArray()))
    private val h1 = HoldId(UUID.nameUUIDFromBytes("h1".toByteArray()))
    private val h2 = HoldId(UUID.nameUUIDFromBytes("h2".toByteArray()))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        fixture =
            AxonTestFixture.with(
                HoldsToFulfillConfiguration.configure(EventSourcingConfigurer.create(), InMemoryHoldsToFulfillRepository()),
            )
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    @Test
    fun `INV-HTF-1 INV-HTF-2 - given two holds on a free tool, when listed via the query gateway, then first hold is at front`() {
        fixture
            .given()
            .events(HoldPlaced(h1, drill, ben, now), HoldPlaced(h2, drill, cleo, now))
            .then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val rows = queryGateway.queryMany(ListToolsToFulfill, HoldsToFulfillRow::class.java).join()
                    assertThat(rows).containsExactly(HoldsToFulfillRow(drill, h1, ben))
                }
            }
    }
}
