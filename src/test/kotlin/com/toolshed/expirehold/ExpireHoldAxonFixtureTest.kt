package com.toolshed.expirehold

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.suspendneighbor.NeighborSuspended
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.AxonTestPhase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** One test per scenario in slices/expire-hold.md, built from the slice's own configure function. Now is 00:10 on 18 October. */
class ExpireHoldAxonFixtureTest {
    private val now = Instant.parse("2026-10-18T06:10:00Z")
    private val earlier = Instant.parse("2026-10-14T16:00:00Z")
    private val oct17 = LocalDate.of(2026, 10, 17)

    private val drill = ToolId(uuid("drill"))
    private val ben = NeighborId(uuid("ben"))
    private val cleo = NeighborId(uuid("cleo"))
    private val h1 = HoldId(uuid("h1"))
    private val h2 = HoldId(uuid("h2"))
    private val h9 = HoldId(uuid("h9"))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        fixture = AxonTestFixture.with(ExpireHoldConfiguration.configure(EventSourcingConfigurer.create(), clockAt(now)))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun clockAt(instant: Instant) = Clock.fixed(instant, ZoneId.of("America/Denver"))

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    /** Ben's H1 is set aside, collect by 17 October; Cleo's H2 is waiting. */
    private fun setAside(): List<Any> =
        listOf(
            ToolAdded(drill, "Cordless drill", null, 7, earlier),
            NeighborRegistered(ben, "Ben Cole", "ben@example.org", earlier),
            NeighborRegistered(cleo, "Cleo Lind", "cleo@example.org", earlier),
            HoldPlaced(h1, drill, ben, earlier),
            HoldPlaced(h2, drill, cleo, earlier),
            HoldFulfilled(h1, drill, ben, oct17, earlier),
        )

    private fun checkout(who: NeighborId) = ToolCheckedOut(LoanId(uuid("loan-$who")), drill, who, 7, LocalDate.of(2026, 10, 25), now)

    private fun assertRefused(
        events: List<Any>,
        hold: HoldId,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        fixture
            .given()
            .recorded(events)
            .`when`()
            .command(ExpireHold(hold))
            .then()
            .exceptionSatisfies { ex ->
                val rejected =
                    generateSequence<Throwable>(ex) { it.cause }.filterIsInstance<ExpireHoldRejected>().firstOrNull()
                requireNotNull(rejected) { "no ExpireHoldRejected in the exception chain: $ex" }
                assertThat(rejected).isInstanceOf(CommandRejected::class.java)
                assertThat(rejected.invariant).isEqualTo(invariant)
                assertThat(rejected.reason).isEqualTo(reason)
                assertThat(rejected.message).isEqualTo(sentence)
            }.noEvents()
    }

    private fun assertEnded(events: List<Any>) = assertRefused(events, h1, "INV-EXP-3", "hold-ended", "This hold has already ended.")

    @Test
    fun `INV-EXP-2 - given H1 set aside collect by 17 October, when Expire Hold at 00-10 on 18 October, then Hold Expired for H1`() {
        fixture
            .given()
            .recorded(setAside())
            .`when`()
            .command(ExpireHold(h1))
            .then()
            .success()
            .events(HoldExpired(h1, now))
    }

    @Test
    fun `INV-EXP-2 - given H1 set aside collect by 17 October, when Expire Hold at 23-59 on 17 October, then refused pickup-window-open`() {
        fixture.stop()
        fixture =
            AxonTestFixture.with(
                ExpireHoldConfiguration.configure(EventSourcingConfigurer.create(), clockAt(Instant.parse("2026-10-18T05:59:00Z"))),
            )
        assertRefused(setAside(), h1, "INV-EXP-2", "pickup-window-open", "This hold's pickup window hasn't ended.")
    }

    @Test
    fun `INV-EXP-3 - given the holder collected, when Expire Hold, then refused hold-ended`() {
        assertEnded(setAside() + checkout(ben))
    }

    @Test
    fun `INV-EXP-3 - given the hold was cancelled, when Expire Hold, then refused hold-ended`() {
        assertEnded(setAside() + HoldCancelled(h1, earlier))
    }

    @Test
    fun `INV-EXP-3 - given the tool was retired, when Expire Hold, then refused hold-ended`() {
        assertEnded(setAside() + ToolRetired(drill, earlier))
    }

    @Test
    fun `INV-EXP-3 - given the holder closed their account, when Expire Hold, then refused hold-ended`() {
        assertEnded(setAside() + NeighborAccountClosed(ben, false, earlier))
    }

    @Test
    fun `INV-EXP-3 - given Cleo checked the tool out after the cut-off, when Expire Hold for H1, then Hold Expired for H1`() {
        fixture
            .given()
            .recorded(setAside() + checkout(cleo))
            .`when`()
            .command(ExpireHold(h1))
            .then()
            .success()
            .events(HoldExpired(h1, now))
    }

    @Test
    fun `INV-EXP-3 - given Ben took the lapsed set-aside nobody waits for, when Expire Hold for H1, then refused hold-ended`() {
        assertEnded(setAside().filterNot { it is HoldPlaced && it.holdId == h2 } + checkout(ben))
    }

    @Test
    fun `INV-EXP-4 - given Ben is suspended, when Expire Hold for H1, then Hold Expired for H1`() {
        fixture
            .given()
            .recorded(setAside() + NeighborSuspended(ben, earlier))
            .`when`()
            .command(ExpireHold(h1))
            .then()
            .success()
            .events(HoldExpired(h1, now))
    }

    @Test
    fun `INV-EXP-10 - given H2 is waiting and never fulfilled, when Expire Hold for H2, then refused hold-not-set-aside`() {
        assertRefused(setAside(), h2, "INV-EXP-10", "hold-not-set-aside", "This hold hasn't been set aside.")
    }

    @Test
    fun `INV-EXP-10 - given no hold H9, when Expire Hold for H9, then refused hold-not-found`() {
        assertRefused(setAside(), h9, "INV-EXP-10", "hold-not-found", "There is no such hold.")
    }

    @Test
    fun `INV-EXP-11 - given Hold Expired recorded for H1, when Expire Hold for H1 again, then succeeds and records nothing`() {
        fixture
            .given()
            .recorded(setAside() + HoldExpired(h1, now))
            .`when`()
            .command(ExpireHold(h1))
            .then()
            .success()
            .noEvents()
    }
}
