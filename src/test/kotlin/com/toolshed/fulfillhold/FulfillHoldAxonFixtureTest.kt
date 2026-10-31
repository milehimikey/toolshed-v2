package com.toolshed.fulfillhold

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.placehold.HoldPlaced
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.AxonTestPhase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** One test per scenario in slices/fulfill-hold.md, built from the slice's own configure function. Today is 10 October. */
class FulfillHoldAxonFixtureTest {
    // 10 October, 10:00 in the shed's zone.
    private val now = Instant.parse("2026-10-10T16:00:00Z")
    private val oct13 = LocalDate.of(2026, 10, 13)
    private val oct12 = LocalDate.of(2026, 10, 12)

    private val drill = ToolId(uuid("drill"))
    private val ana = NeighborId(uuid("ana"))
    private val ben = NeighborId(uuid("ben"))
    private val cara = NeighborId(uuid("cara"))
    private val l1 = LoanId(uuid("l1"))
    private val h1 = HoldId(uuid("h1"))
    private val h2 = HoldId(uuid("h2"))
    private val h9 = HoldId(uuid("h9"))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val clock = Clock.fixed(now, ZoneId.of("America/Denver"))
        val idleFulfiller = HoldFulfiller({ emptyList() }, { error("the fulfiller is not under test here") })
        fixture = AxonTestFixture.with(FulfillHoldConfiguration.configure(EventSourcingConfigurer.create(), clock, idleFulfiller))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    /** Ana has the drill out; Ben's H1 was placed before Cara's H2. */
    private fun waitingQueue(): List<Any> =
        listOf(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            NeighborRegistered(ben, "Ben Cole", "ben@example.org", now),
            NeighborRegistered(cara, "Cara Lind", "cara@example.org", now),
            ToolCheckedOut(l1, drill, ana, 7, oct12, now),
            HoldPlaced(h1, drill, ben, now),
            HoldPlaced(h2, drill, cara, now),
        )

    private fun returned(): List<Any> = waitingQueue() + ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 10), false)

    private fun setAsideForBen(): List<Any> = returned() + HoldFulfilled(h1, drill, ben, oct13, now)

    private fun refusing(
        events: List<Any>,
        hold: HoldId,
    ): Throwable =
        assertThrows<Throwable> {
            val phase =
                fixture
                    .given()
                    .recorded(events)
                    .`when`()
                    .command(FulfillHold(hold))
            phase.then().noEvents()
        }

    private fun assertRejected(
        ex: Throwable,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        val rejected =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<FulfillHoldRejected>()
                .firstOrNull()
        requireNotNull(rejected) { "no FulfillHoldRejected in the exception chain: $ex" }
        assertThat(rejected).isInstanceOf(CommandRejected::class.java)
        assertThat(rejected.invariant).isEqualTo(invariant)
        assertThat(rejected.reason).isEqualTo(reason)
        assertThat(rejected.message).isEqualTo(sentence)
    }

    @Test
    fun `INV-FUL-2 INV-FUL-5 - given the drill returned with H1 and H2 waiting, when H1 is fulfilled, then Ben collects by 13 October`() {
        fixture
            .given()
            .recorded(returned())
            .`when`()
            .command(FulfillHold(h1))
            .then()
            .success()
            .events(HoldFulfilled(h1, drill, ben, oct13, now))
    }

    @Test
    fun `INV-FUL-6 - given the set-aside for Ben expired and H2 is waiting, when H2 is fulfilled, then Hold Fulfilled for Cara`() {
        fixture
            .given()
            .recorded(setAsideForBen() + HoldExpired(h1, now))
            .`when`()
            .command(FulfillHold(h2))
            .then()
            .success()
            .events(HoldFulfilled(h2, drill, cara, oct13, now))
    }

    @Test
    fun `INV-FUL-6 - given Ben cancelled the set-aside H1 and H2 is waiting, when H2 is fulfilled, then Hold Fulfilled for H2`() {
        fixture
            .given()
            .recorded(setAsideForBen() + HoldCancelled(h1, now))
            .`when`()
            .command(FulfillHold(h2))
            .then()
            .success()
            .events(HoldFulfilled(h2, drill, cara, oct13, now))
    }

    @Test
    fun `INV-FUL-6 - given Ben closed his account after H1 was set aside, when H2 is fulfilled, then Hold Fulfilled for H2`() {
        fixture
            .given()
            .recorded(setAsideForBen() + NeighborAccountClosed(ben, false, now))
            .`when`()
            .command(FulfillHold(h2))
            .then()
            .success()
            .events(HoldFulfilled(h2, drill, cara, oct13, now))
    }

    @Test
    fun `INV-FUL-8 - given the drill is back and Ben is suspended, when H1 is fulfilled, then Hold Fulfilled for H1`() {
        fixture
            .given()
            .recorded(returned() + NeighborSuspended(ben, now))
            .`when`()
            .command(FulfillHold(h1))
            .then()
            .success()
            .events(HoldFulfilled(h1, drill, ben, oct13, now))
    }

    @Test
    fun `INV-FUL-8 - given the drill is back and Ben has an overdue loan, when H1 is fulfilled, then Hold Fulfilled for H1`() {
        val saw = ToolId(uuid("saw"))
        fixture
            .given()
            .recorded(returned() + ToolCheckedOut(LoanId(uuid("l9")), saw, ben, 7, LocalDate.of(2026, 10, 1), now))
            .`when`()
            .command(FulfillHold(h1))
            .then()
            .success()
            .events(HoldFulfilled(h1, drill, ben, oct13, now))
    }

    @Test
    fun `INV-FUL-10 - given Hold Fulfilled is recorded for H1, when H1 is fulfilled again, then succeeds and records nothing`() {
        fixture
            .given()
            .recorded(setAsideForBen())
            .`when`()
            .command(FulfillHold(h1))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-FUL-3 - given Ben checked out before the fulfiller acted, when H1 is fulfilled, then refused hold-ended`() {
        val collected = returned() + ToolCheckedOut(LoanId(uuid("l2")), drill, ben, 7, oct13, now)
        assertRejected(refusing(collected, h1), "INV-FUL-3", "hold-ended", "This hold has already ended.")
    }

    @Test
    fun `INV-FUL-2 - given H1 and H2 waiting on a returned drill, when H2 is fulfilled, then refused not-front-of-queue`() {
        assertRejected(
            refusing(returned(), h2),
            "INV-FUL-2",
            "not-front-of-queue",
            "This hold isn't at the front of its tool's queue.",
        )
    }

    @Test
    fun `INV-FUL-4 - given the set-aside for Ben lapsed but not expired, when H2 is fulfilled, then refused tool-set-aside`() {
        assertRejected(
            refusing(setAsideForBen(), h2),
            "INV-FUL-4",
            "tool-set-aside",
            "This tool is already set aside for a holder.",
        )
    }

    @Test
    fun `INV-FUL-3 - given the drill is on loan to Ana, when H1 is fulfilled, then refused tool-checked-out`() {
        assertRejected(refusing(waitingQueue(), h1), "INV-FUL-3", "tool-checked-out", "This tool is already checked out.")
    }

    @Test
    fun `INV-FUL-3 - given Ben cancelled H1 before the fulfiller acted, when H1 is fulfilled, then refused hold-ended`() {
        assertRejected(
            refusing(returned() + HoldCancelled(h1, now), h1),
            "INV-FUL-3",
            "hold-ended",
            "This hold has already ended.",
        )
    }

    @Test
    fun `INV-FUL-9 - given no hold H9 was placed, when H9 is fulfilled, then refused hold-not-found`() {
        assertRejected(refusing(returned(), h9), "INV-FUL-9", "hold-not-found", "There is no such hold.")
    }
}
