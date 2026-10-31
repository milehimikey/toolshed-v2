package com.toolshed.flagneighbor

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ReactionOutcome
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.neighborstoflag.ListNeighborsToFlag
import com.toolshed.neighborstoflag.NeighborsToFlag
import com.toolshed.neighborstoflag.NeighborsToFlagConfiguration
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.returntool.ToolReturned
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.axonframework.common.configuration.Configuration
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.AxonTestPhase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Late Return Flagger on Neighbors To Flag's processor (Amendment 11): the real projection and the flagger run on the
 * shared processor, the list is not stubbed, and Flag Neighbor is the real command handler.
 */
class LateReturnFlaggerHandlerTest {
    private val today = LocalDate.of(2026, 10, 5)
    private val now: Instant = today.atTime(9, 0).toInstant(ZoneOffset.UTC)
    private val ana = NeighborId(UUID.nameUUIDFromBytes("ana".toByteArray()))
    private val bo = NeighborId(UUID.nameUUIDFromBytes("bo".toByteArray()))
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val l2 = LoanId(UUID.nameUUIDFromBytes("l2".toByteArray()))
    private val l3 = LoanId(UUID.nameUUIDFromBytes("l3".toByteArray()))
    private val l4 = LoanId(UUID.nameUUIDFromBytes("l4".toByteArray()))

    private val sent = CopyOnWriteArrayList<Any>()
    private val outcomes = CopyOnWriteArrayList<ReactionOutcome>()
    private val reads = AtomicInteger()
    private val configuration = CompletableFuture<Configuration>()
    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val flagger =
            LateReturnFlaggerHandler(
                readList = {
                    reads.incrementAndGet()
                    configuration
                        .get()
                        .getComponent(QueryGateway::class.java)
                        .query(ListNeighborsToFlag(), NeighborsToFlag::class.java)
                        .join()
                },
                send = { command ->
                    sent.add(command)
                    val outcome =
                        try {
                            configuration.get().getComponent(CommandGateway::class.java).sendAndWait(command)
                            ReactionOutcome.Accepted
                        } catch (e: Exception) {
                            ReactionOutcome.Refused("", e.message ?: "")
                        }
                    outcomes.add(outcome)
                    outcome
                },
            )
        val configurer =
            FlagNeighborConfiguration.configure(
                NeighborsToFlagConfiguration.configure(EventSourcingConfigurer.create(), clock, listOf(flagger)),
                clock,
            )
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun registered(): AxonTestPhase.Given =
        fixture
            .given()
            .execute { configuration.complete(it) }
            .event(NeighborRegistered(ana, "Ana", "ana@example.com", now))
            .event(NeighborRegistered(bo, "Bo", "bo@example.com", now))

    private fun AxonTestPhase.Given.returned(
        loan: LoanId,
        on: LocalDate,
        late: Boolean = true,
        by: NeighborId = ana,
    ): AxonTestPhase.Given =
        event(ToolCheckedOut(loan, drill, by, 7, on.minusDays(1), now))
            .event(ToolReturned(loan, drill, now, on, late))

    private fun AxonTestPhase.Given.twoLateReturns(): AxonTestPhase.Given =
        returned(l1, LocalDate.of(2026, 9, 1)).returned(l2, LocalDate.of(2026, 9, 20))

    @Test
    fun `INV-FLG-2 - given Ana has two late returns, when the third is recorded, then the flagger sends Flag Neighbor from that return`() {
        registered()
            .twoLateReturns()
            .returned(l3, today)
            .`when`()
            .nothing()
            .then()
            .noEvents()
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(sent).containsExactly(FlagNeighbor(ana, listOf(l1, l2, l3)))
            assertThat(outcomes).containsExactly(ReactionOutcome.Accepted)
        }
    }

    @Test
    fun `INV-FLG-2 - given Ana has two late returns, when her third return is on time, then the flagger sends nothing`() {
        // Bo's late return after it is the marker: once the flagger has read for it, Ana's on-time return was handled before.
        registered()
            .twoLateReturns()
            .returned(l3, today, late = false)
            .returned(l4, today, by = bo)
            .`when`()
            .nothing()
            .then()
            .noEvents()
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted { assertThat(reads.get()).isEqualTo(1) }
        assertThat(sent).isEmpty()
    }

    @Test
    fun `INV-FLG-9 - given Ana was flagged by the flagger, when the same flag is sent again, then it succeeds and records nothing`() {
        registered()
            .twoLateReturns()
            .returned(l3, today)
            .execute {
                Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted {
                    assertThat(outcomes).containsExactly(ReactionOutcome.Accepted)
                }
            }.`when`()
            .command(FlagNeighbor(ana, listOf(l1, l2, l3)))
            .then()
            .success()
            .noEvents()
    }
}
