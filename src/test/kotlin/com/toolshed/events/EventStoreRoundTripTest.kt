package com.toolshed.events

import com.toolshed.addtool.ToolAdded
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Publishes the 20 event contracts through a real Axon Server and reads them back through the
 * streaming event source. The stream yields serialized payloads, so each one is decoded through
 * Axon's [EventConverter], which is the real read path.
 */
@SpringBootTest
@Testcontainers
class EventStoreRoundTripTest {
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
    lateinit var eventGateway: EventGateway

    @Autowired
    lateinit var unitOfWorkFactory: UnitOfWorkFactory

    @Autowired
    lateinit var eventSource: StreamableEventSource

    @Autowired
    lateinit var tokenSource: TrackingTokenSource

    @Autowired
    lateinit var eventConverter: EventConverter

    @Test
    fun `all 20 events published through Axon Server read back identical and in order`() {
        val events = SampleEvents.all()
        assertEquals(20, events.size)

        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events) }
        uow.execute().join()

        val read = readFromStart(events)

        assertEquals(events, read)
        // The first sample is a ToolAdded with no description; it must still be null after the round trip.
        assertIs<ToolAdded>(read.first())
        assertEquals(null, (read.first() as ToolAdded).description)
    }

    /** Reads back as many events as [expected] from the start of the store, in stored order. */
    private fun readFromStart(expected: List<Any>): List<Any> {
        val collected = mutableListOf<EventMessage>()
        val deadline = Instant.now().plus(Duration.ofSeconds(15))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (collected.size < expected.size && Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry -> collected.add(entry.message()) }
                    }
                    stream.close()
                }
            }.join()
        assertEquals(expected.size, collected.size, "events read back from Axon Server")
        return collected.mapIndexed { i, message -> decode(message, expected[i].javaClass) }
    }

    private fun decode(
        message: EventMessage,
        type: Class<Any>,
    ): Any =
        checkNotNull(eventConverter.convertPayload<EventMessage, Any>(message, type)) {
            "payload of ${message.type()} did not decode"
        }
}
