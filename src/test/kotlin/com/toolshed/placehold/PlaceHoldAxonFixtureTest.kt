package com.toolshed.placehold

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
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
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

/** One test per scenario in slices/place-hold.md, built from the slice's own configure function. */
class PlaceHoldAxonFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val oct12 = LocalDate.of(2026, 10, 12)
    private val oct5 = LocalDate.of(2026, 10, 5)

    private val t1 = ToolId(uuid("t1"))
    private val t2 = ToolId(uuid("t2"))
    private val t3 = ToolId(uuid("t3"))
    private val t9 = ToolId(uuid("t9"))
    private val ana = NeighborId(uuid("n1"))
    private val ben = NeighborId(uuid("n2"))
    private val cleo = NeighborId(uuid("n3"))
    private val n9 = NeighborId(uuid("n9"))
    private val l1 = LoanId(uuid("l1"))
    private val l2 = LoanId(uuid("l2"))
    private val l3 = LoanId(uuid("l3"))
    private val h1 = HoldId(uuid("h1"))
    private val h2 = HoldId(uuid("h2"))
    private val h3 = HoldId(uuid("h3"))
    private val h4 = HoldId(uuid("h4"))
    private val h5 = HoldId(uuid("h5"))
    private val h6 = HoldId(uuid("h6"))
    private val h7 = HoldId(uuid("h7"))
    private val h8 = HoldId(uuid("h8"))
    private val h9 = HoldId(uuid("h9"))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        fixture = AxonTestFixture.with(PlaceHoldConfiguration.configure(EventSourcingConfigurer.create(), clock))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    /** Ana has the cordless drill (T1) on loan L1, due 12 October; Ben and Cleo are active. */
    private fun shedWithDrillOnLoan(): List<Any> =
        listOf(
            ToolAdded(t1, "Cordless drill", null, 7, now),
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            NeighborRegistered(ben, "Ben Cole", "ben@example.org", now),
            NeighborRegistered(cleo, "Cleo Park", "cleo@example.org", now),
            ToolCheckedOut(l1, t1, ana, 7, oct12, now),
        )

    private fun place(
        hold: HoldId = h2,
        tool: ToolId = t1,
        by: NeighborId = cleo,
    ) = PlaceHold(hold, tool, by)

    private fun placed(
        hold: HoldId,
        tool: ToolId = t1,
        by: NeighborId,
    ) = HoldPlaced(hold, tool, by, now)

    private fun assertRejected(
        ex: Throwable,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        val rejected =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<PlaceHoldRejected>()
                .firstOrNull()
        requireNotNull(rejected) { "no PlaceHoldRejected in the exception chain: $ex" }
        assertThat(rejected.invariant).isEqualTo(invariant)
        assertThat(rejected.reason).isEqualTo(reason)
        assertThat(rejected.message).isEqualTo(sentence)
    }

    // --- placing on a tool that is not available ---

    @Test
    fun `INV-HOLD-2 INV-HOLD-5 given Ana has the drill on loan with no holds, when Ben places hold H1, then recorded`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan())
            .`when`()
            .command(PlaceHold(h1, t1, ben))
            .then()
            .success()
            .events(placed(h1, by = ben))
    }

    @Test
    fun `INV-HOLD-5 given Ben's hold H1 is waiting, when Cleo places hold H2, then recorded behind H1`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan() + placed(h1, by = ben))
            .`when`()
            .command(place())
            .then()
            .success()
            .events(placed(h2, by = cleo))
    }

    @Test
    fun `INV-HOLD-2 given the drill was returned and set aside for Ben's hold H1, when Cleo places hold H2, then recorded`() {
        fixture
            .given()
            .recorded(
                shedWithDrillOnLoan() +
                    placed(h1, by = ben) +
                    ToolReturned(l1, t1, now, oct5, false) +
                    HoldFulfilled(h1, t1, ben, oct12, now),
            ).`when`()
            .command(place())
            .then()
            .success()
            .events(placed(h2, by = cleo))
    }

    @Test
    fun `INV-HOLD-2 given the drill just returned with Ben's H1 waiting, when Cleo places H2, then recorded behind H1`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan() + placed(h1, by = ben) + ToolReturned(l1, t1, now, oct5, false))
            .`when`()
            .command(place())
            .then()
            .success()
            .events(placed(h2, by = cleo))
    }

    @Test
    fun `INV-HOLD-2 given the ladder is in the shed with no holds, when Ben places a hold, then refused tool-available`() {
        fixture
            .given()
            .recorded(
                shedWithDrillOnLoan() + ToolAdded(t2, "Ladder", null, 7, now),
            ).`when`()
            .command(PlaceHold(h1, t2, ben))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-HOLD-2", "tool-available", "This tool is available. Check it out instead.")
            }
    }

    @Test
    fun `INV-HOLD-2 given the ladder is free and the drill has no holds, when Ben places a hold on the drill, then recorded`() {
        // The available check is per tool: a drill on loan elsewhere does not make the ladder unavailable.
        fixture
            .given()
            .recorded(shedWithDrillOnLoan() + ToolAdded(t2, "Ladder", null, 7, now))
            .`when`()
            .command(PlaceHold(h1, t1, ben))
            .then()
            .success()
            .events(placed(h1, by = ben))
    }

    @Test
    fun `INV-HOLD-2 given the drill is set aside for Ben's hold H1 past its collect-by date, when Cleo places hold H8, then recorded`() {
        // The lapsed set-aside still counts as set aside until Hold Expired is recorded, so the tool is not available.
        fixture
            .given()
            .recorded(
                shedWithDrillOnLoan() +
                    placed(h1, by = ben) +
                    ToolReturned(l1, t1, now, oct5, false) +
                    HoldFulfilled(h1, t1, ben, LocalDate.of(2026, 10, 9), now),
            ).`when`()
            .command(PlaceHold(h8, t1, cleo))
            .then()
            .success()
            .events(placed(h8, by = cleo))
    }

    // --- retired and lost tools ---

    @Test
    fun `INV-HOLD-3 given the drill was retired while Ana had it on loan, when Ben places a hold, then refused tool-retired`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan() + ToolRetired(t1, now))
            .`when`()
            .command(PlaceHold(h1, t1, ben))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-HOLD-3", "tool-retired", "This tool has been retired.") }
    }

    @Test
    fun `INV-HOLD-3 given the drill was recorded lost, when Ben places a hold, then refused tool-retired`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan() + ToolLost(l1, t1, now))
            .`when`()
            .command(PlaceHold(h1, t1, ben))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-HOLD-3", "tool-retired", "This tool has been retired.") }
    }

    // --- one hold per neighbor per tool ---

    @Test
    fun `INV-HOLD-7 given Ben's hold H1 on the drill is waiting, when Ben places hold H3 on it, then refused already-holding`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan() + placed(h1, by = ben))
            .`when`()
            .command(PlaceHold(h3, t1, ben))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-HOLD-7", "already-holding", "You already have a hold on this tool.")
            }
    }

    @Test
    fun `INV-HOLD-7 given the drill is set aside for Ben's hold H1, when Ben places hold H3, then refused already-holding`() {
        fixture
            .given()
            .recorded(
                shedWithDrillOnLoan() +
                    placed(h1, by = ben) +
                    ToolReturned(l1, t1, now, oct5, false) +
                    HoldFulfilled(h1, t1, ben, oct12, now),
            ).`when`()
            .command(PlaceHold(h3, t1, ben))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-HOLD-7", "already-holding", "You already have a hold on this tool.")
            }
    }

    @Test
    fun `INV-HOLD-7 given Ben's hold H1 was cancelled and Cleo's H2 is waiting, when Ben places hold H4, then recorded behind H2`() {
        fixture
            .given()
            .recorded(
                shedWithDrillOnLoan() +
                    placed(h1, by = ben) +
                    HoldCancelled(h1, now) +
                    placed(h2, by = cleo),
            ).`when`()
            .command(PlaceHold(h4, t1, ben))
            .then()
            .success()
            .events(placed(h4, by = ben))
    }

    @Test
    fun `INV-HOLD-7 given Ben's hold expired and Cleo now has the drill on loan, when Ben places hold H5, then recorded`() {
        fixture
            .given()
            .recorded(
                shedWithDrillOnLoan() +
                    placed(h1, by = ben) +
                    ToolReturned(l1, t1, now, oct5, false) +
                    HoldFulfilled(h1, t1, ben, oct12, now) +
                    HoldExpired(h1, now) +
                    ToolCheckedOut(l2, t1, cleo, 7, LocalDate.of(2026, 10, 19), now),
            ).`when`()
            .command(PlaceHold(h5, t1, ben))
            .then()
            .success()
            .events(placed(h5, by = ben))
    }

    @Test
    fun `INV-HOLD-7 given Ben's lapsed set-aside has no Hold Expired, when Ben places H9, then refused already-holding`() {
        fixture
            .given()
            .recorded(
                shedWithDrillOnLoan() +
                    placed(h1, by = ben) +
                    ToolReturned(l1, t1, now, oct5, false) +
                    HoldFulfilled(h1, t1, ben, LocalDate.of(2026, 10, 9), now),
            ).`when`()
            .command(PlaceHold(h9, t1, ben))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-HOLD-7", "already-holding", "You already have a hold on this tool.")
            }
    }

    // --- tool on loan to the neighbor ---

    @Test
    fun `INV-HOLD-8 given Ana has the drill on loan, when Ana places a hold on it, then refused tool-on-loan-to-you`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan())
            .`when`()
            .command(PlaceHold(h1, t1, ana))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-HOLD-8", "tool-on-loan-to-you", "You have this tool on loan.") }
    }

    // --- suspension and closure ---

    @Test
    fun `INV-HOLD-9 given Ben is suspended, when Ben places a hold on the drill, then refused neighbor-suspended`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan() + NeighborSuspended(ben, now))
            .`when`()
            .command(PlaceHold(h1, t1, ben))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-HOLD-9", "neighbor-suspended", "You can't place holds while your account is suspended.")
            }
    }

    @Test
    fun `INV-HOLD-9 given Ben was suspended and reinstated, when Ben places a hold on the drill, then recorded`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan() + NeighborSuspended(ben, now) + NeighborReinstated(ben, now))
            .`when`()
            .command(PlaceHold(h1, t1, ben))
            .then()
            .success()
            .events(placed(h1, by = ben))
    }

    @Test
    fun `INV-HOLD-4 given Ben's account is closed, when a hold on the drill is requested for Ben, then refused account-closed`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan() + NeighborAccountClosed(ben, false, now))
            .`when`()
            .command(PlaceHold(h1, t1, ben))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-HOLD-4", "account-closed", "This account is closed.") }
    }

    // --- overdue loans, several holds ---

    @Test
    fun `INV-HOLD-10 given Ben has the ladder on loan overdue, when Ben places hold H6 on the drill, then recorded`() {
        fixture
            .given()
            .recorded(
                shedWithDrillOnLoan() +
                    ToolAdded(t2, "Ladder", null, 7, now) +
                    ToolCheckedOut(l3, t2, ben, 7, oct5, now),
            ).`when`()
            .command(PlaceHold(h6, t1, ben))
            .then()
            .success()
            .events(placed(h6, by = ben))
    }

    @Test
    fun `INV-HOLD-11 given Ben's H1 is waiting and the trimmer is out with Ana, when Ben places H7 on the trimmer, then recorded`() {
        fixture
            .given()
            .recorded(
                shedWithDrillOnLoan() +
                    placed(h1, by = ben) +
                    ToolAdded(t3, "Hedge trimmer", null, 7, now) +
                    ToolCheckedOut(l2, t3, ana, 7, oct12, now),
            ).`when`()
            .command(PlaceHold(h7, t3, ben))
            .then()
            .success()
            .events(placed(h7, tool = t3, by = ben))
    }

    // --- identity: the holder named must be the signed-in neighbor ---

    @Test
    fun `INV-HOLD-1 given the drill is out with Ana, when a request from Ben asks for a hold for Cleo, then refused requester-mismatch`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller(neighborId = ben, isShedManager = false)) { requireHolder(PlaceHold(h1, t1, cleo)) }
            }
        assertThat(refused.reason).isEqualTo("requester-mismatch")
        assertThat(refused.invariant).isEqualTo("INV-HOLD-1")
        assertThat(refused.message).isEqualTo("You can only place holds for yourself.")
    }

    @Test
    fun `INV-HOLD-15 given nobody is signed in, when a request for placing a hold arrives, then refused not-signed-in`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller.ANONYMOUS) { requireHolder(PlaceHold(h1, t1, ben)) }
            }
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.invariant).isEqualTo("INV-HOLD-15")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }

    // --- unknown tool and neighbor ---

    @Test
    fun `INV-HOLD-14 given no tool has ID T9, when Ben asks to place a hold on it, then refused tool-not-found`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan())
            .`when`()
            .command(PlaceHold(h1, t9, ben))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-HOLD-14", "tool-not-found", "There is no such tool.") }
    }

    @Test
    fun `INV-HOLD-14 given no account has neighbor ID N9, when a hold on the drill is requested for N9, then refused neighbor-not-found`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan())
            .`when`()
            .command(PlaceHold(h1, t1, n9))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-HOLD-14", "neighbor-not-found", "There is no such neighbor.") }
    }

    // --- repeats and hold IDs ---

    @Test
    fun `INV-HOLD-13 given H1 was placed on the drill for Ben, when the same request arrives again, then records nothing`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan() + placed(h1, by = ben))
            .`when`()
            .command(PlaceHold(h1, t1, ben))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-HOLD-13 given H1 was placed then cancelled, when the same request arrives again, then records nothing`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan() + placed(h1, by = ben) + HoldCancelled(h1, now))
            .`when`()
            .command(PlaceHold(h1, t1, ben))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-HOLD-13 given H1 was placed on the drill for Ben, when a request reuses H1 for the trimmer, then refused hold-id-in-use`() {
        fixture
            .given()
            .recorded(shedWithDrillOnLoan() + placed(h1, by = ben))
            .`when`()
            .command(PlaceHold(h1, t3, ben))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-HOLD-13", "hold-id-in-use", "That hold ID is already used by another hold.")
            }
    }
}
