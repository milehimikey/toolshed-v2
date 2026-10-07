package com.toolshed.axon

import com.toolshed.axon.tokenprobe.ProbeApplication
import com.toolshed.axon.tokenprobe.ProbeEvent
import com.toolshed.axon.tokenprobe.TokenProbe
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.gateway.EventGateway
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.UUID

/**
 * Every pooled-streaming processor runs exactly one segment, and one segment applies events in the recorded order.
 * The segment proof reads the processor's segments from the foundation's token store bean (after the processor has
 * started and claimed), which is the store the processor actually uses. Runs against a real Axon Server.
 */
@Testcontainers
class ProcessorSegmentTest {
    companion object {
        @Container
        @JvmStatic
        val axonServer = AxonServerContainer().withDcbContext(true)

        /** The processor is named after its EventProcessorDefinition: every handler (the probe's too) runs in it. */
        const val PROBE_PROCESSOR = "single-segment"
    }

    private fun start(): ConfigurableApplicationContext =
        SpringApplicationBuilder(ProbeApplication::class.java).run(
            "--axon.axonserver.servers=${axonServer.axonServerAddress}",
            "--spring.main.web-application-type=none",
            "--spring.profiles.active=probes",
        )

    @Test
    fun `the probe processor runs one segment and applies events in recorded order`() {
        val context = start()
        try {
            val ids = List(10) { UUID.randomUUID() }
            val uow = context.getBean(UnitOfWorkFactory::class.java).create()
            uow.onInvocation { ctx -> context.getBean(EventGateway::class.java).publish(ctx, ids.map { ProbeEvent(it) }) }
            uow.execute().join()

            val probe = context.getBean(TokenProbe::class.java)
            Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted { assertThat(probe.seen).containsAll(ids) }
            assertThat(probe.order.filter { it in ids }).containsExactlyElementsOf(ids)

            val store = context.getBean(TokenStore::class.java)
            val factory = context.getBean(UnitOfWorkFactory::class.java)
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted {
                val segments =
                    factory.create().executeWithResult { ctx -> store.fetchSegments(PROBE_PROCESSOR, ctx) }.join()
                assertThat(segments.map { it.segmentId }).containsExactly(0)
            }
        } finally {
            context.close()
        }
    }
}
