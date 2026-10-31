package com.toolshed.sendpickupnotice

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandRejected
import com.toolshed.axon.CommandSender
import com.toolshed.axon.EmailGateway
import com.toolshed.axon.EmailKey
import com.toolshed.axon.HandOffResult
import com.toolshed.axon.HoldId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ReactionDispatcher
import com.toolshed.axon.ReactionOutcome
import com.toolshed.axon.ToolId
import com.toolshed.cancelhold.HoldCancelled
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.fulfillhold.HoldFulfilled
import com.toolshed.pickupnoticestosend.InMemoryPickupNoticesToSendRepository
import com.toolshed.pickupnoticestosend.PickupNoticesToSendConfiguration
import com.toolshed.placehold.HoldPlaced
import com.toolshed.registerneighbor.NeighborRegistered
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
 * The Pickup Notice Gateway end to end, built from the slices' own configure functions with the real Pickup Notices To Send
 * projection (Amendment 11: the gateway acts from the very event that put the hold on the list) and the real outbox over
 * a recording email gateway. A reaction-side test: negative, idempotency, and the hand-off retries of INV-PKN-4.
 */
class PickupNoticeGatewayFixtureTest {
    private val now = Instant.parse("2026-10-14T16:00:00Z")
    private val oct17 = LocalDate.of(2026, 10, 17)
    private val drill = ToolId(uuid("drill"))
    private val ben = NeighborId(uuid("ben"))
    private val cara = NeighborId(uuid("cara"))
    private val h1 = HoldId(uuid("h1"))
    private val h2 = HoldId(uuid("h2"))

    private val repository = InMemoryPickupNoticesToSendRepository()
    private val sentCommands = CopyOnWriteArrayList<SendPickupNotice>()
    private val handOffs = CopyOnWriteArrayList<HandOff>()
    private val failuresLeft = AtomicInteger(0)
    private val refusalsLeft = AtomicInteger(0)
    private lateinit var configuration: Configuration
    private lateinit var fixture: AxonTestFixture

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
        val gateway =
            PickupNoticeGateway(
                list = { repository.pickupsToNotify() },
                send = { command ->
                    sentCommands.add(command)
                    dispatcher().send(command)
                },
            )
        val clock = Clock.fixed(now, ZoneId.of("America/Denver"))
        val configurer = EventSourcingConfigurer.create()
        SendPickupNoticeConfiguration.configure(configurer, clock)
        SendPickupNoticeConfiguration.configureOutbox(configurer, email)
        PickupNoticesToSendConfiguration.configure(configurer, repository, listOf(PickupNoticeGatewayHandler(gateway)))
        // AxonTestFixture.with disables Axon Server (a bare configurer.start() dials it and fails UNAVAILABLE).
        configurer.lifecycleRegistry { it.onStart(Int.MAX_VALUE, Consumer<Configuration> { started -> configuration = started }) }
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    private fun known(): List<Any> =
        listOf(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            NeighborRegistered(ben, "Ben Ito", "ben@example.org", now),
            NeighborRegistered(cara, "Cara Lind", "cara@example.org", now),
            HoldPlaced(h1, drill, ben, now),
        )

    private val fulfilled = HoldFulfilled(h1, drill, ben, oct17, now)
    private val expected = SendPickupNotice(h1, "ben@example.org", "Ben Ito", "Cordless drill", oct17)

    private fun awaitOneEmail() =
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(handOffs).hasSize(1)
        }

    @Test
    fun `INV-PKN-6 INV-PKN-7 INV-PKN-4 - given H1 waiting, when Hold Fulfilled lands, then command sent and one email keyed H1`() {
        fixture.given().events(known()).`when`().event(fulfilled).then().expect { _ ->
            awaitOneEmail()
            assertThat(sentCommands).containsExactly(expected)
            val out = handOffs.single()
            assertThat(out.key).isEqualTo(EmailKey.ForHold(h1))
            assertThat(out.to).isEqualTo("ben@example.org")
            // INV-PKN-7: name, tool, collect-by date, and what happens if it isn't collected.
            assertThat(out.body).contains("Ben Ito", "Cordless drill", "Saturday 17 October", "expires", "next neighbor")
            assertThat(repository.pickupsToNotify()).isEmpty()
        }
    }

    @Test
    fun `INV-PKN-10 - given H1 is waiting, when an event lands that sets nothing aside, then no command is sent and no email goes out`() {
        fixture.given().events(known()).`when`().event(HoldPlaced(h2, drill, cara, now)).then().expect { _ ->
            Thread.sleep(500)
            assertThat(sentCommands).isEmpty()
            assertThat(handOffs).isEmpty()
        }
    }

    @Test
    fun `INV-PKN-5 - given H1 was cancelled before it was set aside, when Hold Fulfilled never lands, then no email goes out`() {
        fixture
            .given()
            .events(
                known() + HoldCancelled(h1, now),
            ).`when`()
            .event(NeighborEmailChanged(ben, "b@example.org", now))
            .then()
            .expect { _ ->
                Thread.sleep(500)
                assertThat(sentCommands).isEmpty()
                assertThat(handOffs).isEmpty()
            }
    }

    @Test
    fun `INV-PKN-2 - given email handed over, when same command is sent again, then succeeds and nothing new handed over`() {
        fixture.given().events(known()).`when`().event(fulfilled).then().expect { _ ->
            awaitOneEmail()
            assertThat(dispatcher().send(expected)).isEqualTo(ReactionOutcome.Accepted)
            Thread.sleep(500)
            assertThat(handOffs).hasSize(1)
        }
    }

    @Test
    fun `INV-PKN-2 - given email handed over, when another address is sent, then refused notice-already-sent, nothing handed over`() {
        fixture.given().events(known()).`when`().event(fulfilled).then().expect { _ ->
            awaitOneEmail()
            // The fixture has no Spring CommandRejectionInterceptor, so the refusal surfaces as the thrown rejection.
            val failure =
                runCatching { dispatcher().send(expected.copy(sentTo = "ben.ito@example.net")) }.exceptionOrNull()
            val rejected = generateSequence(failure) { it.cause }.filterIsInstance<CommandRejected>().firstOrNull()
            assertThat(rejected).isNotNull
            assertThat(rejected!!.reason).isEqualTo("notice-already-sent")
            Thread.sleep(500)
            assertThat(handOffs).hasSize(1)
        }
    }

    @Test
    fun `INV-PKN-4 - given first hand-off errors, when repeated, then one email goes out`() {
        failuresLeft.set(1)
        fixture.given().events(known()).`when`().event(fulfilled).then().expect { _ ->
            awaitOneEmail()
            assertThat(handOffs.single().key).isEqualTo(EmailKey.ForHold(h1))
        }
    }

    @Test
    fun `INV-PKN-4 - given provider refuses first hand-off, when repeated with same key, then one email goes out`() {
        refusalsLeft.set(1)
        fixture.given().events(known()).`when`().event(fulfilled).then().expect { _ ->
            awaitOneEmail()
            assertThat(handOffs.single().key).isEqualTo(EmailKey.ForHold(h1))
        }
    }

    @Test
    fun `INV-PKN-4 - given notice recorded before start, when outbox starts, then it hands over from the recorded event`() {
        val recorded = PickupNoticeSent(h1, "ben@example.org", "Ben Ito", "Cordless drill", oct17, now)
        fixture
            .given()
            .events(
                known() + fulfilled + recorded,
            ).`when`()
            .event(NeighborEmailChanged(ben, "b@example.org", now))
            .then()
            .expect { _ ->
                awaitOneEmail()
                assertThat(handOffs.single().to).isEqualTo("ben@example.org")
                assertThat(handOffs.single().key).isEqualTo(EmailKey.ForHold(h1))
            }
    }

    @Test
    fun `INV-PKN-3 - given notice recorded to old address, when Ben changes his email, then email goes to recorded address`() {
        val recorded = PickupNoticeSent(h1, "ben@example.org", "Ben Ito", "Cordless drill", oct17, now)
        fixture
            .given()
            .events(
                known() + fulfilled + recorded,
            ).`when`()
            .event(NeighborEmailChanged(ben, "ben.ito@example.net", now))
            .then()
            .expect { _ ->
                awaitOneEmail()
                assertThat(handOffs.single().to).isEqualTo("ben@example.org")
                assertThat(handOffs.map { it.to }).doesNotContain("ben.ito@example.net")
            }
    }
}
