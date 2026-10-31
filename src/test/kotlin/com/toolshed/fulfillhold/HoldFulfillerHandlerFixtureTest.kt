package com.toolshed.fulfillhold

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.CommandSender
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ReactionDispatcher
import com.toolshed.axon.ReactionOutcome
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.holdstofulfill.HoldsToFulfillConfiguration
import com.toolshed.holdstofulfill.InMemoryHoldsToFulfillRepository
import com.toolshed.placehold.HoldPlaced
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.returntool.ToolReturned
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.axonframework.common.configuration.Configuration
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.core.conversion.MessageConverter
import org.axonframework.messaging.eventhandling.annotation.EventHandler
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
import java.util.function.Consumer

/**
 * The Hold Fulfiller on Holds To Fulfill's own processor (Amendment 11), built from the two slices' own configure functions
 * with the real projection and the real fulfiller: no stubbed list. The fulfiller must act on the very event that put the
 * item on the list.
 */
class HoldFulfillerHandlerFixtureTest {
    private val now = Instant.parse("2026-10-10T16:00:00Z")
    private val drill = ToolId(uuid("drill"))
    private val ana = NeighborId(uuid("ana"))
    private val ben = NeighborId(uuid("ben"))
    private val cara = NeighborId(uuid("cara"))
    private val l1 = LoanId(uuid("l1"))
    private val h1 = HoldId(uuid("h1"))
    private val h2 = HoldId(uuid("h2"))

    private val repository = InMemoryHoldsToFulfillRepository()
    private val sent = CopyOnWriteArrayList<FulfillHold>()
    private val fulfilledRecorded = CopyOnWriteArrayList<HoldFulfilled>()
    private lateinit var configuration: Configuration
    private lateinit var fixture: AxonTestFixture

    /** Records every Hold Fulfilled the store publishes, so a test can see what was actually set aside. */
    inner class FulfilledRecorder {
        @EventHandler
        fun on(event: HoldFulfilled) {
            fulfilledRecorded.add(event)
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
        val fulfiller =
            HoldFulfiller(
                list = { repository.toolsToFulfill() },
                send = { command ->
                    sent.add(command)
                    dispatcher().send(command)
                },
            )
        val clock = Clock.fixed(now, ZoneId.of("America/Denver"))
        val configurer = EventSourcingConfigurer.create()
        FulfillHoldConfiguration.configure(configurer, clock)
        HoldsToFulfillConfiguration.configure(configurer, repository, listOf(HoldFulfillerHandler(fulfiller), FulfilledRecorder()))
        // AxonTestFixture.with disables Axon Server (a bare configurer.start() dials it and fails UNAVAILABLE).
        configurer.lifecycleRegistry { it.onStart(Int.MAX_VALUE, Consumer<Configuration> { started -> configuration = started }) }
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    /** Ana has the drill out; Ben's H1 was placed before Cara's H2. */
    private fun drillOnLoanWithHoldWaiting(): List<Any> =
        listOf(
            ToolAdded(drill, "Cordless drill", null, 7, now),
            NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now),
            NeighborRegistered(ben, "Ben Cole", "ben@example.org", now),
            NeighborRegistered(cara, "Cara Lind", "cara@example.org", now),
            ToolCheckedOut(l1, drill, ana, 7, LocalDate.of(2026, 10, 12), now),
            HoldPlaced(h1, drill, ben, now),
        )

    private val returned = ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 10), false)

    @Test
    fun `given the drill is on loan with H1 waiting, when Tool Returned lands, then Fulfill Hold is sent for H1 from that same event`() {
        fixture.given().events(drillOnLoanWithHoldWaiting()).`when`().event(returned).then().expect { _ ->
            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                assertThat(sent).containsExactly(FulfillHold(h1))
                assertThat(repository.toolsToFulfill()).isEmpty()
            }
        }
    }

    @Test
    fun `given the drill is free with no hold, when Hold Placed lands, then Fulfill Hold is sent for it from that same event`() {
        val free = drillOnLoanWithHoldWaiting().filter { it !is HoldPlaced && it !is ToolCheckedOut }
        fixture.given().events(free).`when`().event(HoldPlaced(h1, drill, ben, now)).then().expect { _ ->
            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                assertThat(sent).containsExactly(FulfillHold(h1))
            }
        }
    }

    @Test
    fun `given the drill is on loan with H1 waiting, when a Hold Placed lands that puts no tool on the list, then nothing is sent`() {
        fixture
            .given()
            .events(drillOnLoanWithHoldWaiting())
            .`when`()
            .event(HoldPlaced(h2, drill, cara, now))
            .then()
            .expect { _ ->
                Thread.sleep(500)
                assertThat(sent).isEmpty()
                assertThat(fulfilledRecorded).isEmpty()
            }
    }

    @Test
    fun `given H1 and H2 wait on the drill, when the return fulfils H1 and Fulfill Hold is repeated, then nothing is set aside twice`() {
        val queue = drillOnLoanWithHoldWaiting() + HoldPlaced(h2, drill, cara, now)
        fixture.given().events(queue).`when`().event(returned).then().expect { _ ->
            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                assertThat(sent).containsExactly(FulfillHold(h1))
                assertThat(repository.toolsToFulfill()).isEmpty()
                assertThat(fulfilledRecorded.map { it.holdId }).containsExactly(h1)
            }
            assertThat(dispatcher().send(FulfillHold(h1))).isEqualTo(ReactionOutcome.Accepted)
            Thread.sleep(500)
            assertThat(sent).containsExactly(FulfillHold(h1))
            assertThat(repository.toolsToFulfill()).isEmpty()
            assertThat(fulfilledRecorded.map { it.holdId }).containsExactly(h1)
        }
    }
}
