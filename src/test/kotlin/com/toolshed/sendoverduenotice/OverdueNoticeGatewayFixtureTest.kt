package com.toolshed.sendoverduenotice

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.CommandSender
import com.toolshed.axon.EmailGateway
import com.toolshed.axon.EmailKey
import com.toolshed.axon.HandOffResult
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ReactionDispatcher
import com.toolshed.axon.ReactionOutcome
import com.toolshed.axon.ToolId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.overdueloanstonotify.InMemoryOverdueLoansToNotifyRepository
import com.toolshed.overdueloanstonotify.OverdueLoansToNotifyConfiguration
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.returntool.ToolReturned
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.axonframework.common.configuration.Configuration
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.core.conversion.MessageConverter
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

/**
 * The Overdue Notice Gateway end to end, built from the slices' own configure functions with the real Overdue Loans To Notify
 * projection and the real outbox over a recording email gateway. The gateway is timer-driven (Amendment 7), so each test runs
 * one gateway read itself: the list, the command, the recorded event, then the hand-off from that event (INV-OVN-4).
 */
class OverdueNoticeGatewayFixtureTest {
    private val zone = ZoneId.of("America/Denver")
    private val oct5 = Instant.parse("2026-10-05T16:00:00Z")
    private val oct13 = Instant.parse("2026-10-13T16:00:00Z")
    private val oct12 = LocalDate.of(2026, 10, 12)
    private val drill = ToolId(uuid("drill"))
    private val ana = NeighborId(uuid("ana"))
    private val l1 = LoanId(uuid("l1"))

    private val repository = InMemoryOverdueLoansToNotifyRepository()
    private val sentCommands = CopyOnWriteArrayList<SendOverdueNotice>()
    private val handOffs = CopyOnWriteArrayList<HandOff>()
    private val failuresLeft = AtomicInteger(0)
    private val refusalsLeft = AtomicInteger(0)
    private lateinit var configuration: Configuration
    private lateinit var fixture: AxonTestFixture
    private lateinit var gateway: OverdueNoticeGateway

    data class HandOff(
        val key: EmailKey,
        val to: String,
        val subject: String,
        val body: String,
    )

    private val email =
        object : EmailGateway {
            override fun handOff(
                key: EmailKey,
                to: String,
                subject: String,
                body: String,
            ): HandOffResult {
                if (failuresLeft.getAndDecrement() > 0) throw IllegalStateException("connection error")
                if (refusalsLeft.getAndDecrement() > 0) return HandOffResult.REFUSED
                handOffs.add(HandOff(key, to, subject, body))
                return HandOffResult.ACCEPTED
            }
        }

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private fun dispatcher() =
        ReactionDispatcher(
            CommandSender(
                configuration.getComponent(CommandGateway::class.java),
                configuration.getComponent(MessageConverter::class.java),
                10,
            ),
        )

    @BeforeEach
    fun beforeEach() {
        val clock = Clock.fixed(oct13, zone)
        gateway =
            OverdueNoticeGateway(
                list = { repository.overdue(clock.instant().atZone(zone).toLocalDate()) },
                send = { command ->
                    sentCommands.add(command)
                    dispatcher().send(command)
                },
            )
        val configurer = EventSourcingConfigurer.create()
        SendOverdueNoticeConfiguration.configure(configurer, clock)
        SendOverdueNoticeConfiguration.configureOutbox(configurer, email)
        OverdueLoansToNotifyConfiguration.configure(configurer, repository, clock)
        // AxonTestFixture.with disables Axon Server (a bare configurer.start() dials it and fails UNAVAILABLE).
        configurer.lifecycleRegistry { it.onStart(Int.MAX_VALUE, Consumer<Configuration> { started -> configuration = started }) }
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    private fun known(): List<Any> =
        listOf(
            ToolAdded(drill, "Cordless drill", null, 7, oct5),
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", oct5),
            ToolCheckedOut(l1, drill, ana, 7, oct12, oct5),
        )

    private val recorded = OverdueNoticeSent(l1, "ana@example.org", "Ana Ruiz", "Cordless drill", oct12, oct13)
    private val expected = SendOverdueNotice(l1, "ana@example.org", "Ana Ruiz", "Cordless drill", oct12)

    private fun awaitList(rows: Int) =
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(repository.overdue(LocalDate.of(2026, 10, 13))).hasSize(rows)
        }

    private fun awaitOneEmail() =
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(handOffs).hasSize(1)
        }

    private fun whenEvent(
        given: List<Any>,
        event: Any,
        assertions: () -> Unit,
    ) {
        fixture
            .given()
            .events(given)
            .`when`()
            .event(event)
            .then()
            .expect { _ -> assertions() }
    }

    @Test
    fun `INV-OVN-6 INV-OVN-7 INV-OVN-4 - given L1 is on the list, when the gateway reads, then command sent and one email keyed L1`() {
        whenEvent(known(), NeighborEmailChanged(ana, "ana@example.org", oct5)) {
            awaitList(1)
            gateway.run()
            awaitOneEmail()
            assertThat(sentCommands).containsExactly(expected)
            val out = handOffs.single()
            assertThat(out.key).isEqualTo(EmailKey.ForLoan(l1))
            assertThat(out.to).isEqualTo("ana@example.org")
            // INV-OVN-7: name, tool, due date, a request to return it, and that an overdue loan blocks checkouts and pickups.
            assertThat(out.body).contains("Ana Ruiz", "Cordless drill", "Monday 12 October", "return", "check out", "pick up")
        }
    }

    @Test
    fun `INV-OVN-6 - given nothing is overdue, when the gateway reads, then no command is sent and no email goes out`() {
        val notYetDue = ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 13), oct5)
        whenEvent(known().take(2), notYetDue) {
            Thread.sleep(500)
            gateway.run()
            assertThat(sentCommands).isEmpty()
            assertThat(handOffs).isEmpty()
        }
    }

    @Test
    fun `INV-OVN-5 - given L1 was returned, when the gateway reads, then L1 is not on the list and nothing goes out`() {
        whenEvent(known(), ToolReturned(l1, drill, oct13, LocalDate.of(2026, 10, 13), true)) {
            awaitList(0)
            gateway.run()
            assertThat(sentCommands).isEmpty()
            assertThat(handOffs).isEmpty()
        }
    }

    @Test
    fun `INV-OVN-2 - given the notice went out, when the gateway reads again, then L1 is gone and nothing new is handed over`() {
        whenEvent(known(), NeighborEmailChanged(ana, "ana@example.org", oct5)) {
            awaitList(1)
            gateway.run()
            awaitOneEmail()
            awaitList(0)
            gateway.run()
            assertThat(sentCommands).hasSize(1)
            assertThat(dispatcher().send(expected)).isEqualTo(ReactionOutcome.Accepted)
            Thread.sleep(500)
            assertThat(handOffs).hasSize(1)
        }
    }

    @Test
    fun `INV-OVN-2 - given the notice went out, when another address is sent, then refused notice-already-sent, nothing handed over`() {
        whenEvent(known(), NeighborEmailChanged(ana, "ana@example.org", oct5)) {
            awaitList(1)
            gateway.run()
            awaitOneEmail()
            val failure = runCatching { dispatcher().send(expected.copy(sentTo = "ana.ruiz@example.net")) }.exceptionOrNull()
            val rejected = generateSequence(failure) { it.cause }.filterIsInstance<CommandRejected>().firstOrNull()
            assertThat(rejected).isNotNull
            assertThat(rejected!!.reason).isEqualTo("notice-already-sent")
            Thread.sleep(500)
            assertThat(handOffs).hasSize(1)
        }
    }

    @Test
    fun `INV-OVN-4 - given first hand-off errors, when repeated with the same key, then one email goes out`() {
        failuresLeft.set(1)
        whenEvent(known(), NeighborEmailChanged(ana, "ana@example.org", oct5)) {
            awaitList(1)
            gateway.run()
            awaitOneEmail()
            assertThat(handOffs.single().key).isEqualTo(EmailKey.ForLoan(l1))
        }
    }

    @Test
    fun `INV-OVN-4 - given provider refuses first hand-off, when repeated with same key, then one email goes out`() {
        refusalsLeft.set(1)
        whenEvent(known(), NeighborEmailChanged(ana, "ana@example.org", oct5)) {
            awaitList(1)
            gateway.run()
            awaitOneEmail()
            assertThat(handOffs.single().key).isEqualTo(EmailKey.ForLoan(l1))
        }
    }

    @Test
    fun `INV-OVN-4 - given notice recorded before start, when outbox starts, then it hands over from the recorded event`() {
        whenEvent(known() + recorded, NeighborEmailChanged(ana, "ana@example.org", oct5)) {
            awaitOneEmail()
            assertThat(handOffs.single().to).isEqualTo("ana@example.org")
            assertThat(handOffs.single().key).isEqualTo(EmailKey.ForLoan(l1))
            assertThat(sentCommands).isEmpty()
        }
    }

    @Test
    fun `INV-OVN-3 - given notice recorded to old address, when Ana changes her email, then email goes to recorded address`() {
        whenEvent(known() + recorded, NeighborEmailChanged(ana, "ana.ruiz@example.net", oct13)) {
            awaitOneEmail()
            assertThat(handOffs.single().to).isEqualTo("ana@example.org")
            assertThat(handOffs.map { it.to }).doesNotContain("ana.ruiz@example.net")
        }
    }

    @Test
    fun `INV-OVN-3 - given Ana changed her email early, when the gateway reads, then the email goes to the new address`() {
        val changed = NeighborEmailChanged(ana, "ana.ruiz@example.net", oct5)
        whenEvent(known() + changed, changed) {
            awaitList(1)
            gateway.run()
            awaitOneEmail()
            assertThat(handOffs.single().to).isEqualTo("ana.ruiz@example.net")
        }
    }
}
