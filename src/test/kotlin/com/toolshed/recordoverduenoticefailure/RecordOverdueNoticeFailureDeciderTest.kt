package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.LoanId
import com.toolshed.axon.ToolId
import com.toolshed.recordtoollost.ToolLost
import com.toolshed.returntool.ToolReturned
import com.toolshed.sendoverduenotice.OverdueNoticeSent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** The pure decision, with no Axon fixture: each boundary, and the order the first failure is reported in. */
class RecordOverdueNoticeFailureDeciderTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val handler = RecordOverdueNoticeFailureCommandHandler(Clock.fixed(now, ZoneOffset.UTC))
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))

    private fun stateWith(vararg events: Any): RecordOverdueNoticeFailureCommandHandler.State {
        val state = RecordOverdueNoticeFailureCommandHandler.State()
        events.forEach { event ->
            when (event) {
                is OverdueNoticeSent -> state.evolve(event)
                is OverdueNoticeFailed -> state.evolve(event)
                is ToolReturned -> state.evolve(event)
                is ToolLost -> state.evolve(event)
                else -> error("unexpected event ${event::class.simpleName}")
            }
        }
        return state
    }

    private fun sent() = OverdueNoticeSent(l1, "ana@example.org", "Ana Ruiz", "Cordless drill", LocalDate.of(2026, 10, 5), now)

    private fun decide(
        state: RecordOverdueNoticeFailureCommandHandler.State,
        reason: String = "refused",
    ) = handler.decide(RecordOverdueNoticeFailure(l1, reason), state, now)

    private fun refusedWith(
        reason: String,
        block: () -> Unit,
    ) {
        assertThat(assertThrows<RecordOverdueNoticeFailureRejected>(block).reason).isEqualTo(reason)
    }

    // --- INV-ONF-3 and INV-ONF-4: recorded from a sent notice, with the reason and time ---

    @Test
    fun `INV-ONF-3 a refused failure after a sent notice is recorded with its reason and time`() {
        assertThat(decide(stateWith(sent()))).containsExactly(OverdueNoticeFailed(l1, "refused", now))
    }

    @Test
    fun `INV-ONF-4 a failure with no sent notice is refused no-matching-notice`() {
        refusedWith("no-matching-notice") { decide(stateWith()) }
    }

    // --- INV-ONF-6: at most one failure per loan ---

    @Test
    fun `INV-ONF-6 a second failure with the same reason records nothing`() {
        assertThat(decide(stateWith(sent(), OverdueNoticeFailed(l1, "bounced", now)), reason = "bounced")).isEmpty()
    }

    @Test
    fun `INV-ONF-6 a second failure with a different reason is refused failure-already-recorded`() {
        refusedWith("failure-already-recorded") { decide(stateWith(sent(), OverdueNoticeFailed(l1, "bounced", now)), reason = "refused") }
    }

    // --- INV-ONF-8: an ended loan refuses a new failure ---

    @Test
    fun `INV-ONF-8 a loan returned after the notice refuses the failure loan-ended`() {
        refusedWith("loan-ended") { decide(stateWith(sent(), ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 5), false))) }
    }

    @Test
    fun `INV-ONF-8 a loan recorded lost after the notice refuses the failure loan-ended`() {
        refusedWith("loan-ended") { decide(stateWith(sent(), ToolLost(l1, drill, now))) }
    }

    // --- order: the first failure is the reason returned ---

    @Test
    fun `INV-ONF-9 a different reason on an ended loan is reported failure-already-recorded before loan-ended`() {
        val state =
            stateWith(
                sent(),
                OverdueNoticeFailed(l1, "bounced", now),
                ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 5), false),
            )
        refusedWith("failure-already-recorded") { decide(state, reason = "refused") }
    }

    @Test
    fun `INV-ONF-9 a repeat on an ended loan records nothing`() {
        val state =
            stateWith(
                sent(),
                OverdueNoticeFailed(l1, "bounced", now),
                ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 5), false),
            )
        assertThat(decide(state, reason = "bounced")).isEmpty()
    }

    @Test
    fun `INV-ONF-7 an unknown loan is reported no-matching-notice before any other rule`() {
        val state = stateWith(ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 5), false))
        refusedWith("no-matching-notice") { decide(state) }
    }
}
