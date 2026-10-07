package com.toolshed.testsupport

import io.axoniq.framework.testcontainer.AxonServerContainer
import java.time.Duration

/**
 * One Axon Server for the whole test JVM. Starting a container per test class made each suite run start dozens
 * of servers, and under load a start could outrun its timeout. Tests stay isolated by their own unique ids: every
 * read-back filters on the ids its test created, so one test never sees another's events as its own.
 */
object SharedAxonServer {
    private val container: AxonServerContainer by lazy {
        AxonServerContainer()
            .withDcbContext(true)
            .withStartupTimeout(Duration.ofMinutes(3))
            .also {
                it.start()
                Runtime.getRuntime().addShutdownHook(Thread { it.stop() })
            }
    }

    /** The gRPC address the app connects to, passed as `axon.axonserver.servers`. */
    val address: String
        get() = container.axonServerAddress
}
