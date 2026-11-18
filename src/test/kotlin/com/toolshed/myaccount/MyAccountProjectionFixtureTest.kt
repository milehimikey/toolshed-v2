package com.toolshed.myaccount

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.placehold.HoldPlaced
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
import java.time.LocalDate
import java.util.UUID

/** The slice's own wiring, built from [MyAccountConfiguration.configure], answered through the query bus. */
class MyAccountProjectionFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val n1 = NeighborId(UUID.nameUUIDFromBytes("n1".toByteArray()))
    private val n2 = NeighborId(UUID.nameUUIDFromBytes("n2".toByteArray()))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        fixture = AxonTestFixture.with(MyAccountConfiguration.configure(EventSourcingConfigurer.create(), InMemoryMyAccountRepository()))
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    @Test
    fun `INV-MYAC-1 INV-MYAC-4 given registered and suspended, when asked through the query gateway, then only that account, suspended`() {
        fixture
            .given()
            .events(
                NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", now),
                NeighborRegistered(n2, "Ben Ito", "ben@example.org", now),
                NeighborSuspended(n1, now),
            ).then()
            .expect { configuration ->
                val gateway = configuration.getComponent(QueryGateway::class.java)
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val rows = gateway.queryMany(GetMyAccount(n1), MyAccountRow::class.java).join()
                    assertThat(rows).hasSize(1)
                    assertThat(rows.single().name).isEqualTo("Ana Ruiz")
                    assertThat(rows.single().status).isEqualTo(AccountStatus.SUSPENDED)
                }
            }
    }

    @Test
    fun `INV-MYAC-5 INV-MYAC-7 given loans and holds recorded, when closed, then closed and the loan and hold changed nothing`() {
        fixture
            .given()
            .events(
                NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", now),
                ToolCheckedOut(LoanId(UUID.randomUUID()), ToolId(UUID.randomUUID()), n1, 7, LocalDate.of(2026, 10, 13), now),
                HoldPlaced(HoldId(UUID.randomUUID()), ToolId(UUID.randomUUID()), n1, now),
                NeighborAccountClosed(n1, false, now),
            ).then()
            .expect { configuration ->
                val gateway = configuration.getComponent(QueryGateway::class.java)
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val row = gateway.queryMany(GetMyAccount(n1), MyAccountRow::class.java).join().single()
                    assertThat(row.status).isEqualTo(AccountStatus.CLOSED)
                    assertThat(row.canCloseAccount).isFalse()
                }
            }
    }

    @Test
    fun `INV-MYAC-1 given no account N1, when asked through the query gateway, then not found`() {
        fixture
            .given()
            .noPriorActivity()
            .then()
            .expect { configuration ->
                val gateway = configuration.getComponent(QueryGateway::class.java)
                assertThat(gateway.queryMany(GetMyAccount(n1), MyAccountRow::class.java).join()).isEmpty()
            }
    }
}
