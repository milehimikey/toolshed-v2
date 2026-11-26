package com.toolshed.myaccount

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.placehold.HoldPlaced
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

/** My Account through the query bus: events go in through the fixture, the projection applies them, the query answers. */
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
        check: (MyAccount?) -> Boolean,
    ): MyAccount? {
        var last: MyAccount? = null
        Awaitility.await().atMost(Duration.ofSeconds(30)).until {
            last = gateway.query(GetMyAccount(neighbor), MyAccountAnswer::class.java).join().account
            check(last)
        }
        return last
    }

    private fun registeredAna() = NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", now)

    @Test
    fun `INV-MYAC-2 INV-MYAC-3 INV-MYAC-9 INV-MYAC-11 - Neighbor Registered shows the account active with both actions offered`() {
        fixture
            .given()
            .recorded(listOf(registeredAna()))
            .execute { config ->
                val account = queryUntil(config.getComponent(QueryGateway::class.java), n1) { it != null }!!
                assertThat(account.name).isEqualTo("Ana Ruiz")
                assertThat(account.email).isEqualTo("ana@example.org")
                assertThat(account.status).isEqualTo("active")
                assertThat(account.canChangeEmail).isTrue()
                assertThat(account.canCloseAccount).isTrue()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-2 - Neighbor Email Changed replaces the email in the query answer`() {
        fixture
            .given()
            .recorded(listOf(registeredAna(), NeighborEmailChanged(n1, "ana.ruiz@example.net", now)))
            .execute { config ->
                val account = queryUntil(config.getComponent(QueryGateway::class.java), n1) { it?.email == "ana.ruiz@example.net" }!!
                assertThat(account.status).isEqualTo("active")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-4 - suspended then reinstated shows suspended, then active again`() {
        fixture
            .given()
            .recorded(listOf(registeredAna(), NeighborSuspended(n1, now)))
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                assertThat(queryUntil(gateway, n1) { it?.status == "suspended" }!!.canChangeEmail).isTrue()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
        fixture
            .given()
            .recorded(listOf(NeighborReinstated(n1, now)))
            .execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                assertThat(queryUntil(gateway, n1) { it?.status == "active" }).isNotNull()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-5 INV-MYAC-6 - a suspended neighbor who closes shows closed, read-only, with no actions offered`() {
        fixture
            .given()
            .recorded(listOf(registeredAna(), NeighborSuspended(n1, now), NeighborAccountClosed(n1, true, now)))
            .execute { config ->
                val account = queryUntil(config.getComponent(QueryGateway::class.java), n1) { it?.status == "closed" }!!
                assertThat(account.canChangeEmail).isFalse()
                assertThat(account.canCloseAccount).isFalse()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-8 - a new account N4 after closing N1 shows only N4 when asked for N4`() {
        fixture
            .given()
            .recorded(
                listOf(
                    registeredAna(),
                    NeighborAccountClosed(n1, false, now),
                    NeighborRegistered(n4, "Ana Ruiz", "ana@example.org", now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val fresh = queryUntil(gateway, n4) { it != null }!!
                assertThat(fresh.neighborId).isEqualTo(n4)
                assertThat(fresh.status).isEqualTo("active")
                assertThat(queryUntil(gateway, n1) { it != null }!!.status).isEqualTo("closed")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-1 INV-MYAC-7 - another neighbor's change and loans and holds leave N1 unchanged, and an unknown id is not found`() {
        fixture
            .given()
            .recorded(
                listOf(
                    registeredAna(),
                    NeighborRegistered(n2, "Ben Okafor", "ben@example.org", now),
                    NeighborSuspended(n2, now),
                    ToolCheckedOut(LoanId(UUID.randomUUID()), ToolId(UUID.randomUUID()), n1, 7, LocalDate.of(2026, 10, 13), now),
                    HoldPlaced(HoldId(UUID.randomUUID()), ToolId(UUID.randomUUID()), n1, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                queryUntil(gateway, n2) { it?.status == "suspended" }
                val ana = queryUntil(gateway, n1) { it != null }!!
                assertThat(
                    ana,
                ).isEqualTo(MyAccount(n1, "Ana Ruiz", "ana@example.org", "active", canChangeEmail = true, canCloseAccount = true))
                assertThat(gateway.query(GetMyAccount(n9), MyAccountAnswer::class.java).join().account).isNull()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYAC-10 - the same Neighbor Registered applied twice leaves one unchanged account`() {
        fixture
            .given()
            .recorded(listOf(registeredAna(), NeighborEmailChanged(n1, "ana.ruiz@example.net", now), registeredAna()))
            .execute { config ->
                val account = queryUntil(config.getComponent(QueryGateway::class.java), n1) { it?.email == "ana.ruiz@example.net" }!!
                assertThat(account.status).isEqualTo("active")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }
}
