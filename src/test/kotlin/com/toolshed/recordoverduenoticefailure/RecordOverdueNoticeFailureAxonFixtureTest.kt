package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.LoanId
import com.toolshed.axon.ToolId
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.returntool.ToolReturned
import com.toolshed.sendoverduenotice.OverdueNoticeSent
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
import java.time.ZoneOffset
import java.util.UUID

/** One test per scenario in slices/record-overdue-notice-failure.md, built from the slice's own configure function. */
class RecordOverdueNoticeFailureAxonFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val due = LocalDate.of(2026, 10, 5)
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val l2 = LoanId(UUID.nameUUIDFromBytes("l2".toByteArray()))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        fixture = AxonTestFixture.with(RecordOverdueNoticeFailureConfiguration.configure(EventSourcingConfigurer.create(), clock))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    /** Ana's drill loan L1: the overdue notice was sent to her and the drill is still out. */
    private fun noticeSentForL1(): List<Any> = listOf(OverdueNoticeSent(l1, "ana@example.org", "Ana Ruiz", "Cordless drill", due, now))

    private fun report(
        loan: LoanId = l1,
        reason: String = "refused",
    ) = RecordOverdueNoticeFailure(loan, reason)

    private fun assertRejected(
        ex: Throwable,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        val rejected =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<RecordOverdueNoticeFailureRejected>()
                .firstOrNull()
        requireNotNull(rejected) { "no RecordOverdueNoticeFailureRejected in the exception chain: $ex" }
        assertThat(rejected.invariant).isEqualTo(invariant)
        assertThat(rejected.reason).isEqualTo(reason)
        assertThat(rejected.message).isEqualTo(sentence)
    }

    @Test
    fun `INV-ONF-3 INV-ONF-4 given the notice for L1 was sent and the drill is out, when the provider says refused, then recorded`() {
        fixture
            .given()
            .recorded(noticeSentForL1())
            .`when`()
            .command(report(reason = "refused"))
            .then()
            .success()
            .events(OverdueNoticeFailed(l1, "refused", now))
    }

    @Test
    fun `INV-ONF-3 given the notice for L1 was sent, when the provider says bounced, then recorded with reason bounced`() {
        fixture
            .given()
            .recorded(noticeSentForL1())
            .`when`()
            .command(report(reason = "bounced"))
            .then()
            .success()
            .events(OverdueNoticeFailed(l1, "bounced", now))
    }

    @Test
    fun `INV-ONF-4 INV-ONF-7 given no notice was sent for L2, when a report names L2, then refused no-matching-notice`() {
        fixture
            .given()
            .recorded(noticeSentForL1())
            .`when`()
            .command(report(loan = l2))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-ONF-7", "no-matching-notice", "No overdue notice was sent for that loan.")
            }
    }

    @Test
    fun `INV-ONF-8 given the tool was returned after the notice, when a bounced report for L1 arrives, then refused loan-ended`() {
        fixture
            .given()
            .recorded(noticeSentForL1() + ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 5), false))
            .`when`()
            .command(report(reason = "bounced"))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-ONF-8", "loan-ended", "That loan has already ended.") }
    }

    @Test
    fun `INV-ONF-8 given the tool was lost after the notice, when a refused report for L1 arrives, then refused loan-ended`() {
        fixture
            .given()
            .recorded(noticeSentForL1() + ToolLost(l1, drill, now))
            .`when`()
            .command(report(reason = "refused"))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-ONF-8", "loan-ended", "That loan has already ended.") }
    }

    @Test
    fun `INV-ONF-6 INV-ONF-9 given a bounced failure for L1, when the same report arrives again, then nothing new is recorded`() {
        fixture
            .given()
            .recorded(noticeSentForL1() + OverdueNoticeFailed(l1, "bounced", now))
            .`when`()
            .command(report(reason = "bounced"))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-ONF-6 INV-ONF-9 given a bounced failure for L1, when a refused report arrives, then refused failure-already-recorded`() {
        fixture
            .given()
            .recorded(noticeSentForL1() + OverdueNoticeFailed(l1, "bounced", now))
            .`when`()
            .command(report(reason = "refused"))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-ONF-9", "failure-already-recorded", "A failure is already recorded for that notice.")
            }
    }

    @Test
    fun `INV-ONF-6 given a failure for L1 and the tool returned, when the same report arrives, then nothing new is recorded`() {
        // The repeat is recognised even after the loan ended, so one failure never shows twice.
        fixture
            .given()
            .recorded(
                noticeSentForL1() +
                    OverdueNoticeFailed(l1, "bounced", now) +
                    ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 5), false),
            ).`when`()
            .command(report(reason = "bounced"))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-ONF-7 given the command is for a loan with no notice, when it is refused, then CommandRejected is raised`() {
        // The refusal is a CommandRejected subclass, so the controllers can map it to 422 once the foundation fix lands.
        fixture
            .given()
            .`when`()
            .command(report(loan = l2))
            .then()
            .exceptionSatisfies { ex -> assertThat(generateSequence<Throwable>(ex) { it.cause }.any { it is CommandRejected }).isTrue() }
    }
}
