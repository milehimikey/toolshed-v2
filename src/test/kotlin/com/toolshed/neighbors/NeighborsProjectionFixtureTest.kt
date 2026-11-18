package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
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
 * The slice's own wiring (axon-projection-testing): built from [NeighborsConfiguration.configure], so this proves
 * the projection is registered on its processor and the query is answered through the query bus.
 */
class NeighborsProjectionFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val ana = NeighborId(UUID.nameUUIDFromBytes("n1".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("n2".toByteArray()))

    private lateinit var repository: InMemoryNeighborsRepository
    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        repository = InMemoryNeighborsRepository()
        fixture = AxonTestFixture.with(NeighborsConfiguration.configure(EventSourcingConfigurer.create(), repository))
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    @Test
    fun `INV-NBRS-1 INV-NBRS-2 given registrations and a suspension, when listed through the query gateway, then returns both with status`() {
        fixture
            .given()
            .events(
                NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                NeighborRegistered(ben, "Ben Ito", "ben@example.org", now),
                NeighborSuspended(ana, now),
            ).then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val entries = queryGateway.query(ListNeighbors, Neighbors::class.java).join().entries
                    assertThat(entries.map { it.name }).containsExactly("Ana Ruiz", "Ben Ito")
                    assertThat(entries.map { it.status }).containsExactly(NeighborStatus.SUSPENDED, NeighborStatus.ACTIVE)
                }
            }
    }

    @Test
    fun `INV-NBRS-3 INV-NBRS-4 given a closed account, when listed through the query gateway, then still listed as closed`() {
        fixture
            .given()
            .events(
                NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
                NeighborSuspended(ana, now),
                NeighborAccountClosed(ana, true, now),
            ).then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val entries = queryGateway.query(ListNeighbors, Neighbors::class.java).join().entries
                    assertThat(entries.single().status).isEqualTo(NeighborStatus.CLOSED)
                    assertThat(entries.single().canSuspend).isFalse()
                }
            }
    }
}
