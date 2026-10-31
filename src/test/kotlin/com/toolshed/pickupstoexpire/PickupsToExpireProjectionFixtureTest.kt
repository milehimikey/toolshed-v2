package com.toolshed.pickupstoexpire

import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Built from [PickupsToExpireConfiguration.configure]: the list is answered through the query bus (INV-PTE-1, INV-PTE-2, INV-PTE-5). */
class PickupsToExpireProjectionFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val now = Instant.parse("2026-10-10T16:00:00Z")
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("ben".toByteArray()))
    private val h1 = HoldId(UUID.nameUUIDFromBytes("h1".toByteArray()))
    private val collectBy = LocalDate.of(2026, 10, 13)

    private lateinit var fixture: AxonTestFixture

    private fun start(readAt: Instant) {
        fixture =
            AxonTestFixture.with(
                PickupsToExpireConfiguration.configure(
                    EventSourcingConfigurer.create(),
                    InMemoryPickupsToExpireRepository(),
                    Clock.fixed(readAt, zone),
                ),
            )
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    @Test
    fun `INV-PTE-1 INV-PTE-2 - given Hold Fulfilled, when read after the collect-by date via the query gateway, then H1 is due`() {
        start(Instant.parse("2026-10-14T06:00:00Z"))
        fixture
            .given()
            .events(HoldFulfilled(h1, drill, ben, collectBy, now))
            .then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val rows = queryGateway.queryMany(ListPickupsToExpire, PickupToExpireRow::class.java).join()
                    assertThat(rows).containsExactly(PickupToExpireRow(h1, drill, ben, collectBy))
                }
            }
    }

    @Test
    fun `INV-PTE-5 - given Hold Fulfilled then Hold Expired, when read after the collect-by date, then the list is empty`() {
        start(Instant.parse("2026-10-14T06:00:00Z"))
        fixture
            .given()
            .events(HoldFulfilled(h1, drill, ben, collectBy, now), HoldExpired(h1, now))
            .then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted {
                    val rows = queryGateway.queryMany(ListPickupsToExpire, PickupToExpireRow::class.java).join()
                    assertThat(rows).isEmpty()
                }
            }
    }
}
