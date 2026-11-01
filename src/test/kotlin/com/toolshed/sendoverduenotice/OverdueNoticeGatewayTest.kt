package com.toolshed.sendoverduenotice

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ReactionOutcome
import com.toolshed.axon.ToolId
import com.toolshed.overdueloanstonotify.OverdueLoanToNotifyRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

/** The Overdue Notice Gateway's own behaviour: issue the command with each item's content, and treat a refusal as a log line. */
class OverdueNoticeGatewayTest {
    private fun uuid(seed: String) = UUID.nameUUIDFromBytes(seed.toByteArray())

    private val due = LocalDate.of(2026, 10, 12)

    private fun row(seed: String) =
        OverdueLoanToNotifyRow(
            LoanId(uuid(seed)),
            ToolId(uuid("drill")),
            NeighborId(uuid("ana")),
            due,
            "Cordless drill",
            "Ana Ruiz",
            "ana@example.org",
        )

    @Test
    fun `INV-OVN-6 - given two overdue loans listed, when the gateway reads, then it issues Send Overdue Notice for each`() {
        val sent = mutableListOf<SendOverdueNotice>()
        OverdueNoticeGateway({ listOf(row("l1"), row("l2")) }, { sent.add(it).let { ReactionOutcome.Accepted } }).run()
        assertThat(sent).containsExactly(
            SendOverdueNotice(LoanId(uuid("l1")), "ana@example.org", "Ana Ruiz", "Cordless drill", due),
            SendOverdueNotice(LoanId(uuid("l2")), "ana@example.org", "Ana Ruiz", "Cordless drill", due),
        )
    }

    @Test
    fun `INV-OVN-6 - given nothing is overdue, when the gateway reads, then no command is issued`() {
        val sent = mutableListOf<SendOverdueNotice>()
        OverdueNoticeGateway({ emptyList() }, { sent.add(it).let { ReactionOutcome.Accepted } }).run()
        assertThat(sent).isEmpty()
    }

    @Test
    fun `INV-OVN-5 INV-OVN-11 - given the first loan is refused, when the gateway reads, then it moves on to the next loan`() {
        val sent = mutableListOf<SendOverdueNotice>()
        val send = { command: SendOverdueNotice ->
            sent.add(command)
            if (command.loanId == LoanId(uuid("l1"))) ReactionOutcome.Refused("INV-OVN-5", "loan-ended") else ReactionOutcome.Accepted
        }
        OverdueNoticeGateway({ listOf(row("l1"), row("l2")) }, send).run()
        assertThat(sent).hasSize(2)
    }

    @Test
    fun `INV-OVN-2 - given the same loan on two reads, when read twice, then it sends each time`() {
        val sent = mutableListOf<SendOverdueNotice>()
        val gateway = OverdueNoticeGateway({ listOf(row("l1")) }, { sent.add(it).let { ReactionOutcome.Accepted } })
        gateway.run()
        gateway.run()
        assertThat(sent).hasSize(2)
        assertThat(sent[0]).isEqualTo(sent[1])
    }
}
