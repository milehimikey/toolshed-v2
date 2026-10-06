package com.toolshed.recordpickupnoticefailure

import com.toolshed.axon.EmailKey
import com.toolshed.axon.FailureReport
import com.toolshed.axon.FailureReportVerdict
import com.toolshed.axon.FailureReportVerifier
import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.sendpickupnotice.PickupNoticeSent
import io.axoniq.framework.testcontainer.AxonServerContainer
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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

/**
 * The translation through the running application, against Axon Server. The provider is not chosen, so the app's
 * verifier fails closed; this test supplies a verifier that accepts reports signed "ok", and reads the store back.
 */
@SpringBootTest
@Testcontainers
class PickupFailureReportWiringTest {
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

    /** Accepts a report signed "ok", whose raw body reads `hold=<uuid> reason=<reason>`. A test double, not a provider. */
    @TestConfiguration
    class AcceptingVerifierConfiguration {
        @Bean
        @Primary
        fun acceptingVerifier(): FailureReportVerifier =
            object : FailureReportVerifier {
                override fun verify(report: FailureReport): FailureReportVerdict {
                    if (report.signature != "ok") return FailureReportVerdict.Rejected("signature-not-verified")
                    val fields = report.rawBody.split(" ").associate { it.substringBefore("=") to it.substringAfter("=") }
                    val hold = fields["hold"]?.let { HoldId(UUID.fromString(it)) } ?: return FailureReportVerdict.Rejected("malformed")
                    return FailureReportVerdict.Verified(EmailKey.ForHold(hold), fields["reason"].orEmpty())
                }
            }
    }

    @Autowired
    lateinit var translation: PickupFailureReportTranslation

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

    private val now = Instant.now()

    private fun record(vararg events: Any) {
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events.toList()) }
        uow.execute().join()
    }

    private fun report(
        hold: HoldId,
        reason: String,
        signature: String?,
    ) = FailureReport("hold=${hold.value} reason=$reason", signature)

    /** Reads the store from the start for a few seconds and returns the Pickup Notice Failed events for [hold]. */
    private fun failuresFor(hold: HoldId): List<PickupNoticeFailed> {
        val found = mutableListOf<PickupNoticeFailed>()
        val deadline = Instant.now().plus(Duration.ofSeconds(5))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry ->
                            val message: EventMessage = entry.message()
                            if (message.type().name() == PickupNoticeFailed::class.java.name) {
                                eventConverter
                                    .convertPayload<EventMessage, PickupNoticeFailed>(message, PickupNoticeFailed::class.java)
                                    ?.takeIf { it.holdId == hold }
                                    ?.let { found.add(it) }
                            }
                        }
                    }
                    stream.close()
                }
            }.join()
        return found
    }

    @Test
    fun `a verified refused report about a set-aside is recorded through the running app`() {
        val hold = HoldId(UUID.randomUUID())
        val ben = NeighborId(UUID.randomUUID())
        val drill = ToolId(UUID.randomUUID())
        val collectBy = LocalDate.now().plusDays(3)
        record(
            HoldFulfilled(hold, drill, ben, collectBy, now),
            PickupNoticeSent(hold, "ben@example.com", "Ben", "Cordless drill", collectBy, now),
        )

        translation.receive(report(hold, "refused", signature = "ok"))

        assertEquals(1, failuresFor(hold).size)
        assertEquals("refused", failuresFor(hold).single().reason)
    }

    @Test
    fun `negative - an unsigned report records nothing`() {
        val hold = HoldId(UUID.randomUUID())
        val ben = NeighborId(UUID.randomUUID())
        val drill = ToolId(UUID.randomUUID())
        val collectBy = LocalDate.now().plusDays(3)
        record(
            HoldFulfilled(hold, drill, ben, collectBy, now),
            PickupNoticeSent(hold, "ben@example.com", "Ben", "Cordless drill", collectBy, now),
        )

        translation.receive(report(hold, "bounced", signature = null))

        assertEquals(0, failuresFor(hold).size)
    }
}
