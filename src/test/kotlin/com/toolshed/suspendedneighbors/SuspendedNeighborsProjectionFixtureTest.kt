package com.toolshed.suspendedneighbors

import com.toolshed.axon.NeighborId
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
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
 * The slice's own wiring (axon-projection-testing): built from [SuspendedNeighborsConfiguration.configure], so this
 * proves the projection is registered on its processor and the query is answered through the query bus.
 */
class SuspendedNeighborsProjectionFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val ana = NeighborId(UUID.nameUUIDFromBytes("n1".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("n2".toByteArray()))

    private lateinit var repository: InMemorySuspendedNeighborsRepository
    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        repository = InMemorySuspendedNeighborsRepository()
        fixture = AxonTestFixture.with(SuspendedNeighborsConfiguration.configure(EventSourcingConfigurer.create(), repository))
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    @Test
    fun `INV-SPN-1 given a suspension, when listed through the query gateway, then returns Ana's entry`() {
        fixture
            .given()
            .events(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now), NeighborSuspended(ana, now))
            .then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val entries = queryGateway.query(ListSuspendedNeighbors, SuspendedNeighbors::class.java).join().entries
                    assertThat(entries).hasSize(1)
                    assertThat(entries.single().name).isEqualTo("Ana Ruiz")
                    assertThat(entries.single().suspendedAt).isEqualTo(now)
                }
            }
    }

    @Test
    fun `INV-SPN-4 given a suspension then a reinstatement, when listed through the query gateway, then returns nothing`() {
        fixture
            .given()
            .events(
                NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                NeighborSuspended(ana, now),
                NeighborReinstated(ana, now.plusSeconds(3600)),
                NeighborRegistered(ben, "Ben Ito", "ben@example.org", now),
            ).then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                // Ben is registered after the reinstatement: once he is known, the suspension and reinstatement were applied.
                await().atMost(Duration.ofSeconds(5)).until { repository.neighborName(ben) != null }
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    assertThat(queryGateway.query(ListSuspendedNeighbors, SuspendedNeighbors::class.java).join().entries).isEmpty()
                }
            }
    }
}
