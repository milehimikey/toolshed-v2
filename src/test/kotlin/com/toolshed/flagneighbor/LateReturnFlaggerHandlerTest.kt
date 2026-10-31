package com.toolshed.flagneighbor

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ReactionOutcome
import com.toolshed.axon.ToolId
import com.toolshed.neighborstoflag.NeighborToFlag
import com.toolshed.neighborstoflag.NeighborsToFlag
import com.toolshed.returntool.ToolReturned
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** The Late Return Flagger's reaction: a late return makes it read the list and issue Flag Neighbor; nothing else does. */
class LateReturnFlaggerHandlerTest {
    private val now = Instant.parse("2026-10-05T15:00:00Z")
    private val ana = NeighborId(UUID.nameUUIDFromBytes("ana".toByteArray()))
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))
    private val l2 = LoanId(UUID.nameUUIDFromBytes("l2".toByteArray()))
    private val l3 = LoanId(UUID.nameUUIDFromBytes("l3".toByteArray()))
    private val sent = CopyOnWriteArrayList<Any>()
    private var fixture: AxonTestFixture? = null

    private val onList = NeighborsToFlag(listOf(NeighborToFlag(ana, listOf(l1, l2, l3))))

    @AfterEach
    fun afterEach() {
        fixture?.stop()
    }

    private fun flaggerWith(
        list: NeighborsToFlag,
        outcome: ReactionOutcome = ReactionOutcome.Accepted,
    ): AxonTestFixture {
        val configurer =
            FlagNeighborConfiguration.configureFlagger(
                EventSourcingConfigurer.create(),
                readList = { list },
                send = {
                    sent.add(it)
                    outcome
                },
            )
        return AxonTestFixture.with(configurer).also { fixture = it }
    }

    private fun returned(
        late: Boolean,
        loan: LoanId = l3,
    ) = ToolReturned(loan, drill, now, LocalDate.of(2026, 10, 5), late)

    @Test
    fun `INV-FLG-2 - given Ana is on the list, when a late return is recorded, then the flagger issues Flag Neighbor with her loans`() {
        flaggerWith(onList)
            .given()
            .event(returned(late = true))
            .`when`()
            .nothing()
            .then()
            .noEvents()
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(sent).containsExactly(FlagNeighbor(ana, listOf(l1, l2, l3)))
        }
    }

    @Test
    fun `INV-FLG-2 - given Ana is on the list, when an on-time return is recorded, then the flagger issues nothing`() {
        // The late return after it proves the on-time one was already handled: the processor works in order.
        flaggerWith(onList)
            .given()
            .event(returned(late = false, loan = l1))
            .event(returned(late = true, loan = l2))
            .`when`()
            .nothing()
            .then()
            .noEvents()
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted { assertThat(sent).hasSize(1) }
    }

    @Test
    fun `INV-FLG-9 - given the flagger acts on Ana twice, when the second is a repeat, then both are the same command`() {
        flaggerWith(onList)
            .given()
            .event(returned(late = true, loan = l2))
            .event(returned(late = true, loan = l3))
            .`when`()
            .nothing()
            .then()
            .noEvents()
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(sent).containsExactly(FlagNeighbor(ana, listOf(l1, l2, l3)), FlagNeighbor(ana, listOf(l1, l2, l3)))
        }
    }

    @Test
    fun `INV-FLG-4 - given the command is refused, when the flagger acts, then it moves on to the next neighbor`() {
        val bo = NeighborId(UUID.nameUUIDFromBytes("bo".toByteArray()))
        val both = NeighborsToFlag(onList.neighbors + NeighborToFlag(bo, listOf(l1, l2, l3)))
        flaggerWith(both, ReactionOutcome.Refused("INV-FLG-4", "late-returns-changed"))
            .given()
            .event(returned(late = true))
            .`when`()
            .nothing()
            .then()
            .noEvents()
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted { assertThat(sent).hasSize(2) }
    }
}
