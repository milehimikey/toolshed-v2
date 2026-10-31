package com.toolshed.neighborstoflag

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.flagneighbor.NeighborFlagged
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

/** Neighbors To Flag through the query bus: events go in through the fixture, the list query answers. Clock fixed at 5 October. */
class NeighborsToFlagProjectionFixtureTest {
    private val now = Instant.parse("2026-10-05T10:00:00Z")
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val ana = NeighborId(UUID.nameUUIDFromBytes("ana".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val l2 = LoanId(UUID.nameUUIDFromBytes("l2".toByteArray()))
    private val l3 = LoanId(UUID.nameUUIDFromBytes("l3".toByteArray()))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val clock = Clock.fixed(LocalDate.of(2026, 10, 5).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
        fixture = AxonTestFixture.with(NeighborsToFlagConfiguration.configure(EventSourcingConfigurer.create(), clock))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    private fun lateLoan(
        loanId: LoanId,
        returnedOn: LocalDate,
    ) = listOf(
        ToolCheckedOut(loanId, drill, ana, 7, returnedOn.minusDays(1), now),
        ToolReturned(loanId, drill, now, returnedOn, true),
    )

    @Test
    fun `INV-NTF-2 - three late returns within 90 days put Ana on the list with her three loans`() {
        val events =
            lateLoan(l1, LocalDate.of(2026, 9, 1)) + lateLoan(l2, LocalDate.of(2026, 9, 20)) + lateLoan(l3, LocalDate.of(2026, 10, 5))
        fixture
            .given()
            .recorded(events)
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted {
                    val listed =
                        gateway
                            .query(ListNeighborsToFlag(), NeighborsToFlag::class.java)
                            .join()
                            .neighbors
                            .single()
                    assertThat(listed.neighborId).isEqualTo(ana)
                    assertThat(listed.loanIds).containsExactlyInAnyOrder(l1, l2, l3)
                }
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-NTF-3 - Neighbor Flagged takes Ana off the list`() {
        val late =
            lateLoan(l1, LocalDate.of(2026, 9, 1)) + lateLoan(l2, LocalDate.of(2026, 9, 20)) + lateLoan(l3, LocalDate.of(2026, 10, 5))
        val events = late + NeighborFlagged(ana, listOf(l1, l2, l3), now)
        fixture
            .given()
            .recorded(events)
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted {
                    assertThat(gateway.query(ListNeighborsToFlag(), NeighborsToFlag::class.java).join().neighbors).isEmpty()
                }
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }
}
