package com.toolshed.myloans

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.extendloan.LoanExtended
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.sendoverduenotice.OverdueNoticeSent
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

/**
 * My Loans state view through the query bus: events go in through the fixture, the projection applies them, and
 * the list query answers. Each assertion waits until the view has caught up. The clock is fixed so overdue is a
 * known fact rather than a race against the real one.
 */
class MyLoansQueryFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val ladder = ToolId(UUID.nameUUIDFromBytes("ladder".toByteArray()))
    private val ana = NeighborId(UUID.nameUUIDFromBytes("ana".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("ben".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val clock = Clock.fixed(LocalDate.of(2026, 10, 5).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
        fixture = AxonTestFixture.with(MyLoansConfiguration.configure(EventSourcingConfigurer.create(), clock))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    private fun queryUntil(
        gateway: QueryGateway,
        neighbor: NeighborId,
        check: (MyLoans) -> Boolean,
    ): MyLoans {
        var last: MyLoans? = null
        Awaitility.await().atMost(Duration.ofSeconds(30)).until {
            last = gateway.query(GetMyLoans(neighbor), MyLoans::class.java).join()
            check(last!!)
        }
        return last!!
    }

    @Test
    fun `INV-MYL-2 INV-MYL-7 - the query lists a checked-out loan with its tool name and due date`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val loans = queryUntil(gateway, ana) { it.loans.isNotEmpty() }
                val row = loans.loans.single()
                assertThat(row.toolName).isEqualTo("Cordless drill")
                assertThat(row.dueDate).isEqualTo(LocalDate.of(2026, 10, 12))
                assertThat(row.overdue).isFalse()
                assertThat(row.canExtend).isTrue()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYL-3 - Loan Extended replaces the due date in the query answer`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                    LoanExtended(l1, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19), now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val loans = queryUntil(gateway, ana) { it.loans.any { row -> row.dueDate == LocalDate.of(2026, 10, 19) } }
                assertThat(loans.loans.single().dueDate).isEqualTo(LocalDate.of(2026, 10, 19))
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYL-5 - Overdue Notice Sent shows in the query answer as notice sent`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                    OverdueNoticeSent(l1, "ana@example.org", "Ana Ruiz", "Cordless drill", LocalDate.of(2026, 10, 12), now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val loans = queryUntil(gateway, ana) { it.loans.any { row -> row.overdueNotice == "sent" } }
                assertThat(loans.loans.single().overdueNotice).isEqualTo("sent")
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYL-6 - Tool Returned drops the loan from the query answer`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                    ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 11), false),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted {
                    assertThat(gateway.query(GetMyLoans(ana), MyLoans::class.java).join().loans).isEmpty()
                }
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYL-6 - Tool Lost drops the loan from the query answer`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(ladder, "Ladder", null, 3, now),
                    ToolCheckedOut(l1, ladder, ana, 3, LocalDate.of(2026, 10, 12), now),
                    ToolLost(l1, ladder, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted {
                    assertThat(gateway.query(GetMyLoans(ana), MyLoans::class.java).join().loans).isEmpty()
                }
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYL-9 INV-MYL-10 - a hold placed on the loan's tool, then the tool retired, leaves it not extendable with no hold waiting`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                    HoldPlaced(HoldId(UUID.randomUUID()), drill, ben, now),
                    ToolRetired(drill, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val loans = queryUntil(gateway, ana) { it.loans.any { row -> row.toolRetired } }
                val row = loans.loans.single()
                assertThat(row.toolRetired).isTrue()
                assertThat(row.holdWaiting).isFalse()
                assertThat(row.canExtend).isFalse()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYL-1 - Ana's query never returns Ben's loan`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolAdded(ladder, "Ladder", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                    ToolCheckedOut(LoanId(UUID.randomUUID()), ladder, ben, 7, LocalDate.of(2026, 10, 12), now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val loans = queryUntil(gateway, ana) { it.loans.isNotEmpty() }
                assertThat(loans.loans.map { it.toolId }).containsExactly(drill)
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }

    @Test
    fun `INV-MYL-8 - Neighbor Account Closed of a non-holder leaves the loan unchanged`() {
        fixture
            .given()
            .recorded(
                listOf(
                    ToolAdded(drill, "Cordless drill", null, 7, now),
                    ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
                    NeighborAccountClosed(ben, false, now),
                ),
            ).execute { config ->
                val gateway = config.getComponent(QueryGateway::class.java)
                val loans = queryUntil(gateway, ana) { it.loans.isNotEmpty() }
                val row = loans.loans.single()
                assertThat(row.dueDate).isEqualTo(LocalDate.of(2026, 10, 12))
                assertThat(row.holdWaiting).isFalse()
            }.`when`()
            .nothing()
            .then()
            .noEvents()
    }
}
