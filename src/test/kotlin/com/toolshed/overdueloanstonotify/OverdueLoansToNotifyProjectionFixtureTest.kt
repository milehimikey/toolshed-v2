package com.toolshed.overdueloanstonotify

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.sendoverduenotice.OverdueNoticeSent
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

/** Built from [OverdueLoansToNotifyConfiguration.configure]: the list is answered through the query bus (INV-OLN-1, INV-OLN-3, INV-OLN-6). */
class OverdueLoansToNotifyProjectionFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val now = Instant.parse("2026-10-05T16:00:00Z")
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val ana = NeighborId(UUID.nameUUIDFromBytes("ana".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val due = LocalDate.of(2026, 10, 12)

    private lateinit var fixture: AxonTestFixture

    private fun start() {
        fixture =
            AxonTestFixture.with(
                OverdueLoansToNotifyConfiguration.configure(
                    EventSourcingConfigurer.create(),
                    InMemoryOverdueLoansToNotifyRepository(),
                    Clock.fixed(Instant.parse("2026-10-13T06:30:00Z"), zone),
                ),
            )
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    private val opening =
        arrayOf<Any>(
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            ToolAdded(drill, "Cordless drill", null, 7, now),
            ToolCheckedOut(l1, drill, ana, 7, due, now),
        )

    @Test
    fun `INV-OLN-1 INV-OLN-6 - given an overdue checkout, when read via the query gateway, then L1 is returned with its data`() {
        start()
        fixture
            .given()
            .events(*opening)
            .then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val rows = queryGateway.queryMany(ListOverdueLoansToNotify, OverdueLoanToNotifyRow::class.java).join()
                    assertThat(rows)
                        .containsExactly(
                            OverdueLoanToNotifyRow(l1, drill, ana, due, "Cordless drill", "Ana Ruiz", "ana@example.org"),
                        )
                }
            }
    }

    @Test
    fun `INV-OLN-3 - given an overdue loan then Overdue Notice Sent, when read via the query gateway, then the list is empty`() {
        start()
        fixture
            .given()
            .events(*opening, OverdueNoticeSent(l1, "ana@example.org", "Ana Ruiz", "Cordless drill", due, now))
            .then()
            .expect { configuration ->
                val queryGateway = configuration.getComponent(QueryGateway::class.java)
                await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted {
                    val rows = queryGateway.queryMany(ListOverdueLoansToNotify, OverdueLoanToNotifyRow::class.java).join()
                    assertThat(rows).isEmpty()
                }
            }
    }
}
