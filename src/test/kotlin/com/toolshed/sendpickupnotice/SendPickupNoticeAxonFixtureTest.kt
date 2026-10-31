package com.toolshed.sendpickupnotice

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
import com.toolshed.placehold.HoldPlaced
import com.toolshed.retiretool.ToolRetired
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.AxonTestPhase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One test per command-side scenario in slices/send-pickup-notice.md, built from the slice's own configure function. */
class SendPickupNoticeAxonFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val oct14: Instant =
        LocalDate
            .of(2026, 10, 14)
            .atTime(10, 0)
            .atZone(zone)
            .toInstant()
    private val oct18: Instant =
        LocalDate
            .of(2026, 10, 18)
            .atTime(10, 0)
            .atZone(zone)
            .toInstant()
    private val oct17 = LocalDate.of(2026, 10, 17)

    private val h1 = HoldId(UUID.randomUUID())
    private val h2 = HoldId(UUID.randomUUID())
    private val h9 = HoldId(UUID.randomUUID())
    private val drill = ToolId(UUID.randomUUID())
    private val ben = NeighborId(UUID.randomUUID())
    private val cara = NeighborId(UUID.randomUUID())

    private var fixture: AxonTestFixture? = null

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    /** Ben's H1 on the drill, fulfilled on 14 October with a collect-by date of 17 October. [today] is the clock reading. */
    private fun world(today: Instant = oct14): AxonTestPhase.Given {
        fixture?.stop()
        val configurer = SendPickupNoticeConfiguration.configure(EventSourcingConfigurer.create(), Clock.fixed(today, zone))
        val built = AxonTestFixture.with(configurer)
        fixture = built
        return built
            .given()
            .event(HoldPlaced(h1, drill, ben, oct14))
            .event(HoldFulfilled(h1, drill, ben, oct17, oct14))
    }

    private fun send(
        hold: HoldId = h1,
        sentTo: String = "ben@example.org",
        collectBy: LocalDate = oct17,
    ) = SendPickupNotice(hold, sentTo, "Ben Ito", "Cordless drill", collectBy)

    private fun sentEvent(sentTo: String = "ben@example.org") = PickupNoticeSent(h1, sentTo, "Ben Ito", "Cordless drill", oct17, oct14)

    private fun assertRefused(
        reason: String,
        error: Throwable,
    ) {
        val rejected = generateSequence(error) { it.cause }.filterIsInstance<CommandRejected>().firstOrNull()
        assertTrue(rejected != null, "expected a refusal, got $error")
        assertEquals(reason, rejected.reason)
    }

    private fun refusedWith(
        given: AxonTestPhase.Given,
        command: SendPickupNotice,
        reason: String,
    ) {
        given
            .`when`()
            .command(command)
            .then()
            .exceptionSatisfies { assertRefused(reason, it) }
            .noEvents()
    }

    @Test
    fun `INV-PKN-6 INV-PKN-7 - happy path - given H1 set aside, when issued, then Pickup Notice Sent is recorded`() {
        world()
            .`when`()
            .command(send())
            .then()
            .success()
            .events(sentEvent())
    }

    @Test
    fun `INV-PKN-2 - gateway reads the same hold twice - given notice sent, when same command repeats, then nothing recorded`() {
        world()
            .event(sentEvent())
            .`when`()
            .command(send())
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-PKN-2 - repeat with different content - given notice sent, when another address arrives, then refused notice-already-sent`() {
        refusedWith(world().event(sentEvent()), send(sentTo = "ben.ito@example.net"), "notice-already-sent")
    }

    @Test
    fun `INV-PKN-5 - holder collected before the email went out - given Ben checked the drill out, when issued, then refused hold-ended`() {
        refusedWith(world().event(ToolCheckedOut(LoanId(UUID.randomUUID()), drill, ben, 7, oct17, oct14)), send(), "hold-ended")
    }

    @Test
    fun `INV-PKN-5 - hold cancelled before the email went out - given H1 was cancelled, when issued, then refused hold-ended`() {
        refusedWith(world().event(HoldCancelled(h1, oct14)), send(), "hold-ended")
    }

    @Test
    fun `INV-PKN-5 - hold expired before the email went out - given Hold Expired for H1, when issued, then refused hold-ended`() {
        refusedWith(world().event(HoldExpired(h1, oct14)), send(), "hold-ended")
    }

    @Test
    fun `INV-PKN-5 - tool retired before the email went out - given the drill was retired, when issued, then refused hold-ended`() {
        refusedWith(world().event(ToolRetired(drill, oct14)), send(), "hold-ended")
    }

    @Test
    fun `INV-PKN-5 - account closed before email - given Ben closed his account, when issued, then refused hold-ended`() {
        refusedWith(world().event(NeighborAccountClosed(ben, false, oct14)), send(), "hold-ended")
    }

    @Test
    fun `INV-PKN-3 - current email used - given the list's current address, when issued, then Pickup Notice Sent records that address`() {
        world()
            .`when`()
            .command(send(sentTo = "ben.ito@example.net"))
            .then()
            .success()
            .events(sentEvent("ben.ito@example.net"))
    }

    @Test
    fun `INV-PKN-10 - a waiting hold - given Cara's H2 is waiting, when issued for H2, then refused hold-not-set-aside`() {
        refusedWith(world().event(HoldPlaced(h2, drill, cara, oct14)), send(hold = h2), "hold-not-set-aside")
    }

    @Test
    fun `INV-PKN-11 - wrong collect-by date - given 17 October, when 18 October arrives, then refused collect-by-date-mismatch`() {
        refusedWith(world(), send(collectBy = LocalDate.of(2026, 10, 18)), "collect-by-date-mismatch")
    }

    @Test
    fun `INV-PKN-12 - collect-by date passed - given 18 October, no Hold Expired, then refused pickup-window-ended`() {
        refusedWith(world(today = oct18), send(), "pickup-window-ended")
    }

    @Test
    fun `INV-PKN-9 - unknown hold - given no hold H9 was placed, when issued for H9, then refused hold-not-found`() {
        refusedWith(world(), send(hold = h9), "hold-not-found")
    }
}
