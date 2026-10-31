package com.toolshed.undeliverednotices

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.recordoverduenoticefailure.OverdueNoticeFailed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.returntool.ToolReturned
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
 * The slice's own wiring (axon-projection-testing): built from [UndeliveredNoticesConfiguration.configure], so this
 * proves the projection is registered on its processor and the query is answered through the query bus.
 */
class UndeliveredNoticesProjectionFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.nameUUIDFromBytes("t1".toByteArray()))
    private val ana = NeighborId(UUID.nameUUIDFromBytes("n1".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))

    private lateinit var repository: InMemoryUndeliveredNoticesRepository
    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        repository = InMemoryUndeliveredNoticesRepository()
        fixture = AxonTestFixture.with(UndeliveredNoticesConfiguration.configure(EventSourcingConfigurer.create(), repository))
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    private val setup =
        listOf(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
        )

    @Test
    fun `given an overdue notice failure, when listed through the query gateway, then returns Ana's entry`() {
        fixture
            .given()
            .events(*setup.toTypedArray(), OverdueNoticeFailed(l1, "bounced", now))
            .then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val entries = queryGateway.query(ListUndeliveredNotices, UndeliveredNotices::class.java).join().entries
                    assertThat(entries).hasSize(1)
                    assertThat(entries.single().neighborName).isEqualTo("Ana Ruiz")
                    assertThat(entries.single().toolName).isEqualTo("Cordless drill")
                }
            }
    }

    @Test
    fun `given an overdue notice failure then a return, when listed through the query gateway, then returns nothing`() {
        fixture
            .given()
            .events(
                *setup.toTypedArray(),
                OverdueNoticeFailed(l1, "bounced", now),
                ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 14), true),
            ).then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                // Waits for the loan to be recorded as ended first, so an empty result reflects the drop-off
                // and not a processor that simply hasn't caught up yet.
                await().atMost(Duration.ofSeconds(5)).until { repository.openLoanOf(l1) == null && repository.toolName(drill) != null }
                assertThat(queryGateway.query(ListUndeliveredNotices, UndeliveredNotices::class.java).join().entries).isEmpty()
            }
    }
}
