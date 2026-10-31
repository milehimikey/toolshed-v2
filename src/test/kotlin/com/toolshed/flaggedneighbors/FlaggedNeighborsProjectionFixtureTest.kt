package com.toolshed.flaggedneighbors

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.flagneighbor.NeighborFlagged
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.returntool.ToolReturned
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
import java.time.LocalDate
import java.util.UUID

/**
 * The slice's own wiring (axon-projection-testing): built from [FlaggedNeighborsConfiguration.configure], so this
 * proves the projection is registered on its processor and the query is answered through the query bus.
 */
class FlaggedNeighborsProjectionFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val ladder = ToolId(UUID.nameUUIDFromBytes("t1".toByteArray()))
    private val ana = NeighborId(UUID.nameUUIDFromBytes("n1".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))

    private lateinit var repository: InMemoryFlaggedNeighborsRepository
    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        repository = InMemoryFlaggedNeighborsRepository()
        fixture = AxonTestFixture.with(FlaggedNeighborsConfiguration.configure(EventSourcingConfigurer.create(), repository))
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    private val setup =
        listOf(
            ToolAdded(ladder, "Ladder", null, 7, now),
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            ToolCheckedOut(l1, ladder, ana, 7, LocalDate.of(2026, 8, 31), now),
            ToolReturned(l1, ladder, now, LocalDate.of(2026, 9, 1), true),
        )

    @Test
    fun `INV-FLN-1 given a flag, when listed through the query gateway, then returns Ana's entry`() {
        fixture
            .given()
            .events(*setup.toTypedArray(), NeighborFlagged(ana, listOf(l1), now))
            .then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val entries = queryGateway.query(ListFlaggedNeighbors, FlaggedNeighbors::class.java).join().entries
                    assertThat(entries).hasSize(1)
                    assertThat(entries.single().name).isEqualTo("Ana Ruiz")
                    assertThat(
                        entries
                            .single()
                            .lateReturns
                            .single()
                            .toolName,
                    ).isEqualTo("Ladder")
                }
            }
    }

    @Test
    fun `INV-FLN-5 given a flag then a suspension, when listed through the query gateway, then returns nothing`() {
        fixture
            .given()
            .events(*setup.toTypedArray(), NeighborFlagged(ana, listOf(l1), now), NeighborSuspended(ana, now))
            .then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                // Waits until the processor has applied the setup, so an empty result reflects the drop-off.
                await().atMost(Duration.ofSeconds(5)).until { repository.loanOf(l1)?.returnedOn != null }
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    assertThat(queryGateway.query(ListFlaggedNeighbors, FlaggedNeighbors::class.java).join().entries).isEmpty()
                }
            }
    }
}
