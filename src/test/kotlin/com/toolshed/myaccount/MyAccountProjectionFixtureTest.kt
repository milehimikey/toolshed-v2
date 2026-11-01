package com.toolshed.myaccount

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.AxonTestPhase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** My Account state view through the query bus: events go in through the fixture, the projection applies them, the query answers. */
class MyAccountProjectionFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val n1 = NeighborId(UUID.nameUUIDFromBytes("n1".toByteArray()))
    private val n2 = NeighborId(UUID.nameUUIDFromBytes("n2".toByteArray()))
    private val n4 = NeighborId(UUID.nameUUIDFromBytes("n4".toByteArray()))
    private val n9 = NeighborId(UUID.nameUUIDFromBytes("n9".toByteArray()))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        fixture = AxonTestFixture.with(MyAccountConfiguration.configure(EventSourcingConfigurer.create()))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    private fun queryUntil(
        gateway: QueryGateway,
        neighbor: NeighborId,
        check: (MyAccountRow) -> Boolean,
    ): MyAccountRow {
        var last: MyAccountRow? = null
        Awaitility.await().atMost(Duration.ofSeconds(30)).until {
            last = gateway.query(GetMyAccount(neighbor), MyAccount::class.java).join().account
            last != null && check(last!!)
        }
        return last!!
    }

    private fun registered(
        id: NeighborId,
        name: String = "Ana Ruiz",
        email: String = "ana@example.org",
    ) = NeighborRegistered(id, name, email, now)

    @Test
    fun `INV-MYAC-2 INV-MYAC-3 INV-MYAC-9 INV-MYAC-11 - Neighbor Registered shows an active account with both actions offered`() {
        fixture
            .given()
            .recorded(listOf(registered(n1)))
            .execute { config ->
                val row = queryUntil(config.getComponent(QueryGateway::class.java), n1) { true }
                assertThat(row.name).isEqualTo("Ana Ruiz")
                assertThat(row.email).isEqualTo("ana@example.org")
                assertThat(row.status).isEqualTo("active")
                assertThat(row.canChangeEmail).isTrue()
                assertThat(row.canCloseAccount).isTrue()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-2 - Neighbor Email Changed replaces the email in the query answer`() {
        fixture
            .given()
            .recorded(listOf(registered(n1), NeighborEmailChanged(n1, "ana.ruiz@example.net", now)))
            .execute { config ->
                val row = queryUntil(config.getComponent(QueryGateway::class.java), n1) { it.email == "ana.ruiz@example.net" }
                assertThat(row.name).isEqualTo("Ana Ruiz")
                assertThat(row.status).isEqualTo("active")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-4 INV-MYAC-9 INV-MYAC-11 - Neighbor Suspended shows suspended with both actions still offered`() {
        fixture
            .given()
            .recorded(listOf(registered(n1), NeighborSuspended(n1, now)))
            .execute { config ->
                val row = queryUntil(config.getComponent(QueryGateway::class.java), n1) { it.status == "suspended" }
                assertThat(row.canChangeEmail).isTrue()
                assertThat(row.canCloseAccount).isTrue()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-4 - Neighbor Reinstated shows active again`() {
        fixture
            .given()
            .recorded(listOf(registered(n1), NeighborSuspended(n1, now), NeighborReinstated(n1, now)))
            .execute { config ->
                val row = queryUntil(config.getComponent(QueryGateway::class.java), n1) { it.status == "active" }
                assertThat(row.status).isEqualTo("active")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-2 INV-MYAC-4 - email changed after reinstatement shows the new email and active`() {
        fixture
            .given()
            .recorded(
                listOf(
                    registered(n1),
                    NeighborSuspended(n1, now),
                    NeighborReinstated(n1, now),
                    NeighborEmailChanged(n1, "ana.ruiz@example.net", now),
                ),
            ).execute { config ->
                val row = queryUntil(config.getComponent(QueryGateway::class.java), n1) { it.email == "ana.ruiz@example.net" }
                assertThat(row.status).isEqualTo("active")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-5 INV-MYAC-6 - Neighbor Account Closed shows closed with neither action offered`() {
        fixture
            .given()
            .recorded(listOf(registered(n1), NeighborAccountClosed(n1, false, now)))
            .execute { config ->
                val row = queryUntil(config.getComponent(QueryGateway::class.java), n1) { it.status == "closed" }
                assertThat(row.canChangeEmail).isFalse()
                assertThat(row.canCloseAccount).isFalse()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-5 - a suspended neighbor who closes their account sees closed not suspended`() {
        fixture
            .given()
            .recorded(listOf(registered(n1), NeighborSuspended(n1, now), NeighborAccountClosed(n1, true, now)))
            .execute { config ->
                val row = queryUntil(config.getComponent(QueryGateway::class.java), n1) { it.status == "closed" }
                assertThat(row.status).isEqualTo("closed")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-8 - signing up again gives a new active account and the old one stays closed`() {
        fixture
            .given()
            .recorded(listOf(registered(n1), NeighborAccountClosed(n1, false, now), registered(n4)))
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val fresh = queryUntil(gateway, n4) { true }
                assertThat(fresh.neighborId).isEqualTo(n4)
                assertThat(fresh.status).isEqualTo("active")
                assertThat(queryUntil(gateway, n1) { true }.status).isEqualTo("closed")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-1 - Ana's query never returns Ben's account`() {
        fixture
            .given()
            .recorded(listOf(registered(n1), registered(n2, "Ben Ortiz", "ben@example.org")))
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                queryUntil(gateway, n2) { true }
                val row = queryUntil(gateway, n1) { true }
                assertThat(row.neighborId).isEqualTo(n1)
                assertThat(row.name).isEqualTo("Ana Ruiz")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-1 INV-MYAC-7 - another neighbor's suspension leaves Ana's account unchanged`() {
        fixture
            .given()
            .recorded(listOf(registered(n1), registered(n2, "Ben Ortiz", "ben@example.org"), NeighborSuspended(n2, now)))
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                queryUntil(gateway, n2) { it.status == "suspended" }
                assertThat(queryUntil(gateway, n1) { true }.status).isEqualTo("active")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-7 - loans do not change My Account`() {
        fixture
            .given()
            .recorded(
                listOf(
                    registered(n1),
                    ToolCheckedOut(LoanId(UUID.randomUUID()), ToolId(UUID.randomUUID()), n1, 7, LocalDate.of(2026, 10, 12), now),
                    registered(n2, "Ben Ortiz", "ben@example.org"),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                queryUntil(gateway, n2) { true }
                val row = queryUntil(gateway, n1) { true }
                assertThat(row.status).isEqualTo("active")
                assertThat(row.email).isEqualTo("ana@example.org")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-10 - the same Neighbor Registered applied twice leaves one unchanged account`() {
        fixture
            .given()
            .recorded(listOf(registered(n1), registered(n1), registered(n2, "Ben Ortiz", "ben@example.org")))
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                queryUntil(gateway, n2) { true }
                val row = queryUntil(gateway, n1) { true }
                assertThat(row.status).isEqualTo("active")
                assertThat(row.email).isEqualTo("ana@example.org")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-1 - asking for an account that does not exist answers not found`() {
        fixture
            .given()
            .recorded(listOf(registered(n1)))
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                queryUntil(gateway, n1) { true }
                assertThat(gateway.query(GetMyAccount(n9), MyAccount::class.java).join().account).isNull()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }
}
