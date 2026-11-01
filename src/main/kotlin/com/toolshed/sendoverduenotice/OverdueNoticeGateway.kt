package com.toolshed.sendoverduenotice

import com.toolshed.axon.ReactionDispatcher
import com.toolshed.axon.ReactionOutcome
import com.toolshed.overdueloanstonotify.ListOverdueLoansToNotify
import com.toolshed.overdueloanstonotify.OverdueLoanToNotifyRow
import com.toolshed.overdueloanstonotify.OverdueLoansToNotifyQueryHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * The Overdue Notice Gateway's reading half: read Overdue Loans To Notify once and issue Send Overdue Notice for each item,
 * with the item's content. A refusal is logged with the loan id and the gateway moves on; a repeat records nothing, so
 * reading the same loan twice is harmless (INV-OVN-2). The list fills when midnight passes, not on an event, so it is read
 * on a timer (Amendment 7); the hand-off to the provider is the outbox's job, from the recorded event (INV-OVN-4).
 */
class OverdueNoticeGateway(
    private val list: () -> List<OverdueLoanToNotifyRow>,
    private val send: (SendOverdueNotice) -> ReactionOutcome,
) {
    fun run() = list().forEach { handle(it) }

    private fun handle(item: OverdueLoanToNotifyRow) {
        val command = SendOverdueNotice(item.loanId, item.borrowerEmail, item.borrowerName, item.toolName, item.dueDate)
        when (val outcome = send(command)) {
            is ReactionOutcome.Accepted -> logger.debug { "overdue-notice-gateway loanId=${item.loanId.value} outcome=accepted" }
            is ReactionOutcome.Refused ->
                logger.info { "overdue-notice-gateway loanId=${item.loanId.value} outcome=refused reason=${outcome.reason}" }
        }
    }
}

/** The running app's Overdue Notice Gateway: reads Overdue Loans To Notify in-process, through the read slice's query handler. */
@Configuration
@EnableScheduling
class OverdueNoticeGatewayConfiguration {
    @Bean
    fun overdueNoticeGateway(
        list: OverdueLoansToNotifyQueryHandler,
        dispatcher: ReactionDispatcher,
    ): OverdueNoticeGateway =
        OverdueNoticeGateway(
            list = { list.handle(ListOverdueLoansToNotify) },
            send = { dispatcher.send(it) },
        )
}

/** The timer: every 5 minutes, at any hour (Amendment 7). A failed run is logged and the next one still happens. */
@Component
class OverdueNoticeGatewaySchedule(
    private val gateway: OverdueNoticeGateway,
) {
    @Scheduled(fixedDelayString = "\${toolshed.overdue-notice-gateway.interval:PT5M}")
    fun tick() {
        try {
            gateway.run()
        } catch (failure: RuntimeException) {
            logger.warn(failure) { "overdue-notice-gateway run failed" }
        }
    }
}
