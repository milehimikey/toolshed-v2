package com.toolshed.pickupnoticestosend

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.registerneighbor.NeighborRegistered
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
import java.time.LocalDate
import java.util.UUID

/**
 * Built from [PickupNoticesToSendConfiguration.configure]: proves the projection is registered on its processor and the
 * list is answered through the query bus (INV-PNS-1, INV-PNS-2), and that a reaction runs after the write (Amendment 11).
 */
class PickupNoticesToSendProjectionFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val collectBy = LocalDate.of(2026, 10, 17)
    private val drill = ToolId(UUID.nameUUIDFromBytes("t1".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("n2".toByteArray()))
    private val h1 = HoldId(UUID.nameUUIDFromBytes("h1".toByteArray()))

    private lateinit var fixture: AxonTestFixture
    private val seenByReaction = mutableListOf<List<PickupNoticesToSendRow>>()

    @BeforeEach
    fun beforeEach() {
        val repository = InMemoryPickupNoticesToSendRepository()
        val reaction =
            object : PickupNoticesToSendReaction {
                override fun listChanged(event: Any) {
                    if (event is HoldFulfilled) seenByReaction.add(repository.pickupsToNotify())
                }
            }
        fixture =
            AxonTestFixture.with(
                PickupNoticesToSendConfiguration.configure(EventSourcingConfigurer.create(), repository, listOf(reaction)),
            )
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    @Test
    fun `INV-PNS-1 INV-PNS-2 - given Ben and drill known, when Hold Fulfilled, then gateway lists H1 and reaction saw it`() {
        val expected = PickupNoticesToSendRow(h1, drill, ben, collectBy, "Ben Ito", "ben@example.org", "Cordless drill")
        fixture
            .given()
            .events(
                NeighborRegistered(ben, "Ben Ito", "ben@example.org", now),
                ToolAdded(drill, "Cordless drill", null, 7, now),
                HoldFulfilled(h1, drill, ben, collectBy, now),
            ).then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val rows = queryGateway.queryMany(ListPickupsToNotify, PickupNoticesToSendRow::class.java).join()
                    assertThat(rows).containsExactly(expected)
                    assertThat(seenByReaction).containsExactly(listOf(expected))
                }
            }
    }
}
