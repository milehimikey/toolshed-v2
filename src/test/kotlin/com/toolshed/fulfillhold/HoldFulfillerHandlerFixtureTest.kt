package com.toolshed.fulfillhold

import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.placehold.HoldPlaced
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.returntool.ToolReturned
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Hold Fulfiller's processor, built from [FulfillHoldConfiguration.configure]: an event that changes the list wakes
 * the fulfiller (INV-FUL-2); an event that cannot change it does not.
 */
class HoldFulfillerHandlerFixtureTest {
    private val now = Instant.parse("2026-10-10T16:00:00Z")
    private val drill = ToolId(UUID.nameUUIDFromBytes("drill".toByteArray()))
    private val ben = NeighborId(UUID.nameUUIDFromBytes("ben".toByteArray()))
    private val h1 = HoldId(UUID.nameUUIDFromBytes("h1".toByteArray()))
    private val l1 = LoanId(UUID.nameUUIDFromBytes("l1".toByteArray()))

    private val runs = AtomicInteger()
    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val fulfiller = HoldFulfiller({ runs.incrementAndGet().let { emptyList() } }, { error("nothing is on the list") })
        val clock = Clock.fixed(now, ZoneId.of("America/Denver"))
        fixture = AxonTestFixture.with(FulfillHoldConfiguration.configure(EventSourcingConfigurer.create(), clock, fulfiller))
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    @Test
    fun `INV-FUL-2 - given a tool is returned, when Tool Returned lands, then the fulfiller reads the list`() {
        fixture.given().events(ToolReturned(l1, drill, now, LocalDate.of(2026, 10, 10), false)).then().expect { _ ->
            await().atMost(Duration.ofSeconds(5)).untilAsserted { assertThat(runs.get()).isGreaterThanOrEqualTo(1) }
        }
    }

    @Test
    fun `given a hold is placed on a free tool, when Hold Placed lands, then the fulfiller reads the list`() {
        fixture.given().events(HoldPlaced(h1, drill, ben, now)).then().expect { _ ->
            await().atMost(Duration.ofSeconds(5)).untilAsserted { assertThat(runs.get()).isGreaterThanOrEqualTo(1) }
        }
    }

    @Test
    fun `given an event that cannot change the list, when Neighbor Registered lands, then the fulfiller does not act`() {
        fixture.given().events(NeighborRegistered(ben, "Ben Cole", "ben@example.org", now)).then().expect { _ ->
            Thread.sleep(500)
            assertThat(runs.get()).isZero()
        }
    }
}
