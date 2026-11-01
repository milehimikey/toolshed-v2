package com.toolshed.sendoverduenotice

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.extendloan.LoanExtended
import com.toolshed.recordoverduenoticefailure.OverdueNoticeFailed
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.returntool.ToolReturned
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

/** One test per command-side scenario in slices/send-overdue-notice.md, built from the slice's own configure function. */
class SendOverdueNoticeAxonFixtureTest {
    private val zone = ZoneId.of("America/Denver")

    private fun at(
        day: Int,
        hour: Int = 10,
    ): Instant =
        LocalDate
            .of(2026, 10, day)
            .atTime(hour, 0)
            .atZone(zone)
            .toInstant()

    private val oct12 = LocalDate.of(2026, 10, 12)
    private val oct19 = LocalDate.of(2026, 10, 19)

    private val l1 = LoanId(UUID.randomUUID())
    private val l9 = LoanId(UUID.randomUUID())
    private val drill = ToolId(UUID.randomUUID())
    private val ana = NeighborId(UUID.randomUUID())

    private var fixture: AxonTestFixture? = null

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    /** Ana's L1 on the drill, checked out on 5 October due 12 October. [today] is the clock reading. */
    private fun world(today: Instant = at(13)): AxonTestPhase.Given {
        fixture?.stop()
        val configurer = SendOverdueNoticeConfiguration.configure(EventSourcingConfigurer.create(), Clock.fixed(today, zone))
        val built = AxonTestFixture.with(configurer)
        fixture = built
        return built.given().event(ToolCheckedOut(l1, drill, ana, 7, oct12, at(5)))
    }

    private fun send(
        loan: LoanId = l1,
        sentTo: String = "ana@example.org",
        dueDate: LocalDate = oct12,
    ) = SendOverdueNotice(loan, sentTo, "Ana Ruiz", "Cordless drill", dueDate)

    private fun sentEvent(sentTo: String = "ana@example.org") = OverdueNoticeSent(l1, sentTo, "Ana Ruiz", "Cordless drill", oct12, at(13))

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
        command: SendOverdueNotice,
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
    fun `INV-OVN-6 INV-OVN-7 - happy path - given L1 is overdue on 13 October, when issued, then the notice is recorded`() {
        world()
            .`when`()
            .command(send())
            .then()
            .success()
            .events(sentEvent())
    }

    @Test
    fun `INV-OVN-6 - not overdue on the due date itself - given today is 12 October, when issued, then refused loan-not-overdue`() {
        refusedWith(world(today = at(12, 23)), send(), "loan-not-overdue")
    }

    @Test
    fun `INV-OVN-11 - extended before the gateway acted - given due 19 October, when 12 October arrives, then refused due-date-changed`() {
        refusedWith(world().event(LoanExtended(l1, oct12, oct19, at(12))), send(), "due-date-changed")
    }

    @Test
    fun `INV-OVN-5 - returned just before the gateway acted - given Ana returned the drill, when issued, then refused loan-ended`() {
        refusedWith(world().event(ToolReturned(l1, drill, at(13), LocalDate.of(2026, 10, 13), true)), send(), "loan-ended")
    }

    @Test
    fun `INV-OVN-5 - recorded lost before the notice - given Tool Lost for L1, when issued, then refused loan-ended`() {
        refusedWith(world().event(ToolLost(l1, drill, at(13))), send(), "loan-ended")
    }

    @Test
    fun `INV-OVN-2 - the gateway reads the same loan twice - given notice sent, when same command repeats, then nothing recorded`() {
        world()
            .event(sentEvent())
            .`when`()
            .command(send())
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-OVN-2 - a repeat with different content - given notice sent, when another address arrives, then refused already-sent`() {
        refusedWith(world().event(sentEvent()), send(sentTo = "ana.ruiz@example.net"), "notice-already-sent")
    }

    @Test
    fun `INV-OVN-3 - current email used - given the list's current address, when issued, then Overdue Notice Sent records that address`() {
        world()
            .`when`()
            .command(send(sentTo = "ana.ruiz@example.net"))
            .then()
            .success()
            .events(sentEvent("ana.ruiz@example.net"))
    }

    @Test
    fun `INV-OVN-9 - notice that later fails - given Overdue Notice Failed after the notice, when issued anyway, then nothing recorded`() {
        world()
            .event(sentEvent())
            .event(OverdueNoticeFailed(l1, "mailbox-full", at(14)))
            .`when`()
            .command(send())
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-OVN-10 - unknown loan - given no loan L9 was checked out, when issued for L9, then refused loan-not-found`() {
        refusedWith(world(), send(loan = l9), "loan-not-found")
    }
}
