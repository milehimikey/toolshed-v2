package com.toolshed.cancelhold

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.expirehold.HoldExpired
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.placehold.HoldPlaced
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.retiretool.ToolRetired
import com.toolshed.returntool.ToolReturned
import com.toolshed.suspendneighbor.NeighborSuspended
import com.toolshed.web.Caller
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
import java.time.ZoneOffset
import java.util.UUID

/** One test per scenario in slices/cancel-hold.md, built from the slice's own configure function. */
class CancelHoldAxonFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val oct12 = LocalDate.of(2026, 10, 12)
    private val oct5 = LocalDate.of(2026, 10, 5)
    private val oct17 = LocalDate.of(2026, 10, 17)

    private val drill = ToolId(uuid("drill"))
    private val ana = NeighborId(uuid("ana"))
    private val ben = NeighborId(uuid("ben"))
    private val cara = NeighborId(uuid("cara"))
    private val l1 = LoanId(uuid("l1"))
    private val l2 = LoanId(uuid("l2"))
    private val h1 = HoldId(uuid("h1"))
    private val h2 = HoldId(uuid("h2"))
    private val h9 = HoldId(uuid("h9"))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        fixture = AxonTestFixture.with(CancelHoldConfiguration.configure(EventSourcingConfigurer.create(), clock))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    /** The drill is checked out by Ana; Ben's hold H1 was placed before Cara's hold H2, and both are waiting. */
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

    /** The drill is back and set aside for Ben under H1; Cara's H2 is still waiting. */
    private fun setAsideForBen(): List<Any> =
        waitingQueue() +
            listOf(
                ToolReturned(l1, drill, now, oct5, false),
                HoldFulfilled(h1, drill, ben, oct17, now),
            )

    private fun assertRejected(
        ex: Throwable,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        val rejected =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<CancelHoldRejected>()
                .firstOrNull()
        requireNotNull(rejected) { "no CancelHoldRejected in the exception chain: $ex" }
        assertThat(rejected.invariant).isEqualTo(invariant)
        assertThat(rejected.reason).isEqualTo(reason)
        assertThat(rejected.message).isEqualTo(sentence)
    }

    // --- cancelling a live hold ---

    @Test
    fun `INV-CNH-2 given H1 and H2 are waiting, when Ben cancels H1, then Hold Cancelled is recorded`() {
        fixture
            .given()
            .recorded(waitingQueue())
            .`when`()
            .command(CancelHold(ben, h1))
            .then()
            .success()
            .events(HoldCancelled(h1, now))
    }

    @Test
    fun `INV-CNH-2 given the drill is set aside for Ben under H1, when Ben cancels H1, then Hold Cancelled is recorded`() {
        fixture
            .given()
            .recorded(setAsideForBen())
            .`when`()
            .command(CancelHold(ben, h1))
            .then()
            .success()
            .events(HoldCancelled(h1, now))
    }

    @Test
    fun `INV-CNH-2 given the set-aside lapsed without Hold Expired recorded, when Ben cancels H1, then recorded and no expiry follows`() {
        fixture
            .given()
            .recorded(setAsideForBen())
            .`when`()
            .command(CancelHold(ben, h1))
            .then()
            .success()
            .events(HoldCancelled(h1, now))
    }

    @Test
    fun `INV-CNH-2 given Ben is suspended and H1 is waiting, when Ben cancels H1, then Hold Cancelled is recorded`() {
        fixture
            .given()
            .recorded(waitingQueue() + NeighborSuspended(ben, now))
            .`when`()
            .command(CancelHold(ben, h1))
            .then()
            .success()
            .events(HoldCancelled(h1, now))
    }

    // --- repeats ---

    @Test
    fun `INV-CNH-8 given Hold Cancelled was recorded for H1, when the same request arrives again, then nothing new is recorded`() {
        fixture
            .given()
            .recorded(waitingQueue() + HoldCancelled(h1, now))
            .`when`()
            .command(CancelHold(ben, h1))
            .then()
            .success()
            .noEvents()
    }

    // --- identity and ownership ---

    @Test
    fun `INV-CNH-1 given H1 is Ben's waiting hold, when Cara, naming herself, cancels H1, then refused not-your-hold`() {
        fixture
            .given()
            .recorded(waitingQueue())
            .`when`()
            .command(CancelHold(cara, h1))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-CNH-1", "not-your-hold", "You can only cancel your own holds.")
            }
    }

    @Test
    fun `INV-CNH-1 given H1 is Ben's waiting hold, when a request naming Ben comes from Cara, then refused requester-mismatch`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller(neighborId = cara, isShedManager = false)) { requireCanceller(CancelHold(ben, h1)) }
            }
        assertThat(refused.reason).isEqualTo("requester-mismatch")
        assertThat(refused.invariant).isEqualTo("INV-CNH-1")
        assertThat(refused.message).isEqualTo("You can only cancel holds for yourself.")
    }

    @Test
    fun `INV-CNH-1 given a caller with no sign-in, when they ask to cancel H1, then refused not-signed-in`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller.ANONYMOUS) { requireCanceller(CancelHold(ben, h1)) }
            }
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.invariant).isEqualTo("INV-CNH-1")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }

    // --- unknown hold ---

    @Test
    fun `INV-CNH-7 given no hold H9 has been placed, when a request to cancel H9 arrives, then refused hold-not-found`() {
        fixture
            .given()
            .recorded(waitingQueue())
            .`when`()
            .command(CancelHold(ben, h9))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-CNH-7", "hold-not-found", "There is no such hold.")
            }
    }

    // --- a hold that has ended another way ---

    @Test
    fun `INV-CNH-3 given Hold Expired was recorded for H1, when Ben tries to cancel H1, then refused hold-ended`() {
        fixture
            .given()
            .recorded(setAsideForBen() + HoldExpired(h1, now))
            .`when`()
            .command(CancelHold(ben, h1))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CNH-3", "hold-ended", "This hold has already ended.") }
    }

    @Test
    fun `INV-CNH-3 given Ben checked out the drill as H1's holder, when Ben tries to cancel H1, then refused hold-ended`() {
        fixture
            .given()
            .recorded(setAsideForBen() + ToolCheckedOut(l2, drill, ben, 7, oct17, now))
            .`when`()
            .command(CancelHold(ben, h1))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CNH-3", "hold-ended", "This hold has already ended.") }
    }

    @Test
    fun `INV-CNH-3 given the drill was retired while H1 was waiting, when Ben tries to cancel H1, then refused hold-ended`() {
        fixture
            .given()
            .recorded(waitingQueue() + ToolRetired(drill, now))
            .`when`()
            .command(CancelHold(ben, h1))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CNH-3", "hold-ended", "This hold has already ended.") }
    }

    @Test
    fun `INV-CNH-3 given Tool Lost was recorded while H1 was waiting, when Ben tries to cancel H1, then refused hold-ended`() {
        fixture
            .given()
            .recorded(waitingQueue() + ToolLost(l1, drill, now))
            .`when`()
            .command(CancelHold(ben, h1))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CNH-3", "hold-ended", "This hold has already ended.") }
    }

    @Test
    fun `INV-CNH-3 given Ben closed his account while H1 was waiting, when Ben cancels H1, then refused hold-ended`() {
        fixture
            .given()
            .recorded(waitingQueue() + NeighborAccountClosed(ben, false, now))
            .`when`()
            .command(CancelHold(ben, h1))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CNH-3", "hold-ended", "This hold has already ended.") }
    }
}
