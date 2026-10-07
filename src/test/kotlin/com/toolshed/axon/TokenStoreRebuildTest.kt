package com.toolshed.axon

import com.toolshed.axon.tokenprobe.ProbeApplication
import com.toolshed.axon.tokenprobe.ProbeEvent
import com.toolshed.axon.tokenprobe.TokenProbe
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.gateway.EventGateway
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.UUID

/**
 * A view rebuilds on restart: the in-memory token store starts each processor from a fresh token, so a new
 * context replays the stream from the start into the probe. Runs against a real Axon Server.
 */
@Testcontainers
class TokenStoreRebuildTest {
    companion object {
        @Container
        @JvmStatic
        val axonServer = AxonServerContainer().withDcbContext(true)
    }

    private fun start(): ConfigurableApplicationContext =
        SpringApplicationBuilder(ProbeApplication::class.java).run(
            "--axon.axonserver.servers=${axonServer.axonServerAddress}",
            "--spring.main.web-application-type=none",
            "--spring.profiles.active=probes",
        )

    @Test
    fun `a restarted context replays the stream and rebuilds the probe view`() {
        val ids = List(3) { UUID.randomUUID() }

        val first = start()
        val gateway = first.getBean(EventGateway::class.java)
        val uow = first.getBean(UnitOfWorkFactory::class.java).create()
        uow.onInvocation { ctx -> gateway.publish(ctx, ids.map { ProbeEvent(it) }) }
        uow.execute().join()
        val firstProbe = first.getBean(TokenProbe::class.java)
        Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted { assertThat(firstProbe.seen).containsAll(ids) }
        first.close()

        val second = start()
        try {
            val secondProbe = second.getBean(TokenProbe::class.java)
            Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted { assertThat(secondProbe.seen).containsAll(ids) }
        } finally {
            second.close()
        }
    }
}
