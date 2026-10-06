package com.toolshed.recordpickupnoticefailure

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
import com.toolshed.retiretool.ToolRetired
import com.toolshed.sendpickupnotice.PickupNoticeSent
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

/**
 * Scenario tests for Record Pickup Notice Failure, through AxonTestFixture built from the slice's configure function.
 * Each set-aside also has its Hold Fulfilled given, since a pickup email is only sent for a set-aside.
 */
class RecordPickupNoticeFailureAxonFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val now: Instant =
        LocalDate
            .of(2026, 10, 6)
            .atTime(10, 0)
            .atZone(zone)
            .toInstant()
    private val collectBy = LocalDate.of(2026, 10, 9)

    private var fixture: AxonTestFixture? = null

    private val h1 = HoldId(UUID.randomUUID())
    private val h2 = HoldId(UUID.randomUUID())
    private val drill = ToolId(UUID.randomUUID())
    private val ben = NeighborId(UUID.randomUUID())
    private val ana = NeighborId(UUID.randomUUID())

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    /** Ben's set-aside on the drill for H1, with its pickup email sent. */
    private fun world(): AxonTestPhase.Given {
        fixture?.stop()
        val configurer = RecordPickupNoticeFailureConfiguration.configure(EventSourcingConfigurer.create(), Clock.fixed(now, zone))
        val built = AxonTestFixture.with(configurer)
        fixture = built
        return built
            .given()
            .event(HoldFulfilled(h1, drill, ben, collectBy, now))
            .event(PickupNoticeSent(h1, "ben@example.com", "Ben", "Cordless drill", collectBy, now))
    }

    private fun report(
        hold: HoldId,
        reason: String,
    ) = RecordPickupNoticeFailure(hold, reason)

    private fun assertRefused(
        reason: String,
        error: Throwable,
    ) {
        val rejected = generateSequence(error) { it.cause }.filterIsInstance<CommandRejected>().firstOrNull()
        assertTrue(rejected != null, "expected a refusal, got $error")
        assertEquals(reason, rejected.reason)
    }

    @Test
    fun `INV-PNF-3 - given the pickup email for H1 was sent, when the report says refused, then recorded as refused`() {
        world()
            .`when`()
            .command(report(h1, "refused"))
            .then()
            .success()
            .events(PickupNoticeFailed(h1, "refused", now))
    }

    @Test
    fun `INV-PNF-3 - given the pickup email for H1 was sent, when the report says bounced, then recorded as bounced`() {
        world()
            .`when`()
            .command(report(h1, "bounced"))
            .then()
            .success()
            .events(PickupNoticeFailed(h1, "bounced", now))
    }

    @Test
    fun `INV-PNF-8 INV-PNF-5 - given no pickup email was sent for H2, when a report names H2, then refused no-matching-notice`() {
        world()
            .`when`()
            .command(report(h2, "bounced"))
            .then()
            .exceptionSatisfies { assertRefused("no-matching-notice", it) }
    }

    @Test
    fun `INV-PNF-9 - given Ben collected the drill after the email, when the report says bounced, then refused hold-ended`() {
        world()
            .event(ToolCheckedOut(LoanId(UUID.randomUUID()), drill, ben, 7, collectBy, now))
            .`when`()
            .command(report(h1, "bounced"))
            .then()
            .exceptionSatisfies { assertRefused("hold-ended", it) }
    }

    @Test
    fun `INV-PNF-9 - given Hold Expired was recorded for H1, when the report says refused, then refused hold-ended`() {
        world()
            .event(HoldExpired(h1, now))
            .`when`()
            .command(report(h1, "refused"))
            .then()
            .exceptionSatisfies { assertRefused("hold-ended", it) }
    }

    @Test
    fun `INV-PNF-9 - given Hold Cancelled was recorded for H1, when the report says bounced, then refused hold-ended`() {
        world()
            .event(HoldCancelled(h1, now))
            .`when`()
            .command(report(h1, "bounced"))
            .then()
            .exceptionSatisfies { assertRefused("hold-ended", it) }
    }

    @Test
    fun `INV-PNF-9 - given the drill was retired, when the report says bounced, then refused hold-ended`() {
        world()
            .event(ToolRetired(drill, now))
            .`when`()
            .command(report(h1, "bounced"))
            .then()
            .exceptionSatisfies { assertRefused("hold-ended", it) }
    }

    @Test
    fun `INV-PNF-9 - given Ben closed his account, when the report says bounced, then refused hold-ended`() {
        world()
            .event(NeighborAccountClosed(ben, false, now))
            .`when`()
            .command(report(h1, "bounced"))
            .then()
            .exceptionSatisfies { assertRefused("hold-ended", it) }
    }

    @Test
    fun `INV-PNF-4 INV-PNF-10 - given H1 failed bounced, when the same report arrives again, then succeeds and nothing new is recorded`() {
        world()
            .event(PickupNoticeFailed(h1, "bounced", now))
            .`when`()
            .command(report(h1, "bounced"))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-PNF-4 INV-PNF-10 - given H1 failed bounced, when a report says refused, then refused failure-already-recorded`() {
        world()
            .event(PickupNoticeFailed(h1, "bounced", now))
            .`when`()
            .command(report(h1, "refused"))
            .then()
            .exceptionSatisfies { assertRefused("failure-already-recorded", it) }
    }

    @Test
    fun `INV-PNF-8 - given H2 was fulfilled but no pickup email was sent, when a report names H2, then refused no-matching-notice`() {
        world()
            .event(HoldFulfilled(h2, drill, ana, collectBy, now))
            .`when`()
            .command(report(h2, "refused"))
            .then()
            .exceptionSatisfies { assertRefused("no-matching-notice", it) }
    }
}
