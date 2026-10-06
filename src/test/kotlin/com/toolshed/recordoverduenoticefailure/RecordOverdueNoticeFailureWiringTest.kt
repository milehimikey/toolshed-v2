package com.toolshed.recordoverduenoticefailure

import com.toolshed.axon.LoanId
import com.toolshed.sendoverduenotice.OverdueNoticeSent
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.EventMessage
import org.axonframework.messaging.eventhandling.conversion.EventConverter
import org.axonframework.messaging.eventhandling.gateway.EventGateway
import org.axonframework.messaging.eventstreaming.StreamableEventSource
import org.axonframework.messaging.eventstreaming.StreamingCondition
import org.axonframework.messaging.eventstreaming.TrackingTokenSource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The notice-failure slice wired into the running app against a real Axon Server: the handler is found
 * by Spring, the State entity is registered, and the translation drops an unsigned report.
 */
@SpringBootTest
@Testcontainers
class RecordOverdueNoticeFailureWiringTest {
    companion object {
        @Container
        @JvmStatic
        val axonServer = AxonServerContainer().withDcbContext(true)

        @DynamicPropertySource
        @JvmStatic
        fun axonServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("axon.axonserver.servers") { axonServer.axonServerAddress }
        }
    }

    @Autowired
    lateinit var commandGateway: CommandGateway

    @Autowired
    lateinit var translation: OverdueNoticeFailureTranslation

    @Autowired
    lateinit var eventGateway: EventGateway

    @Autowired
    lateinit var unitOfWorkFactory: UnitOfWorkFactory

    @Autowired
    lateinit var eventSource: StreamableEventSource

    @Autowired
    lateinit var tokenSource: TrackingTokenSource

    @Autowired
    lateinit var eventConverter: EventConverter

    private val now = Instant.parse("2026-10-06T04:00:00Z")

    @Test
    fun `INV-ONF-3 INV-ONF-4 given a notice was sent, when the failure command runs in the app, then one failure is recorded`() {
        val loan = LoanId(UUID.randomUUID())
        publish(OverdueNoticeSent(loan, "ana@example.org", "Ana Ruiz", "Cordless drill", LocalDate.of(2026, 10, 5), now))

        commandGateway.sendAndWait(RecordOverdueNoticeFailure(loan, "refused"))

        assertThat(failedFor(loan)).hasSize(1)
    }

    @Test
    fun `INV-ONF-3 given a notice was sent, when an unsigned report arrives, then it is dropped and nothing is recorded`() {
        val loan = LoanId(UUID.randomUUID())
        publish(OverdueNoticeSent(loan, "ana@example.org", "Ana Ruiz", "Cordless drill", LocalDate.of(2026, 10, 5), now))

        translation.receive(ProviderFailureReport(loan.value.toString(), "refused", signature = null))

        assertThat(failedFor(loan)).isEmpty()
    }

    private fun publish(event: Any) {
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, listOf(event)) }
        uow.execute().join()
    }

    /** Reads the store from the start for a few seconds and returns the Overdue Notice Failed events for [loan]. */
    private fun failedFor(loan: LoanId): List<OverdueNoticeFailed> {
        val found = mutableListOf<OverdueNoticeFailed>()
        val deadline = Instant.now().plus(Duration.ofSeconds(5))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry ->
                            val message: EventMessage = entry.message()
                            if (message.type().name() == OverdueNoticeFailed::class.java.name) {
                                decode(message)?.takeIf { it.loanId == loan }?.let { found.add(it) }
                            }
                        }
                    }
                    stream.close()
                }
            }.join()
        return found
    }

    private fun decode(message: EventMessage): OverdueNoticeFailed? =
        eventConverter.convertPayload<EventMessage, OverdueNoticeFailed>(message, OverdueNoticeFailed::class.java)
}
