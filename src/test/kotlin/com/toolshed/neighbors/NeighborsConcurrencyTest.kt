package com.toolshed.neighbors

import com.toolshed.axon.NeighborId
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals

/**
 * Many writers toggle suspension on the same few accounts while a reader keeps listing. Every suspension is followed by its
 * reinstatement, so each account must end active. Plain read-modify-write storage loses updates here, and a plain HashMap
 * fails the listing under concurrent change.
 */
class NeighborsConcurrencyTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")

    @Test
    fun `INV-NBRS-7 - concurrent writers and a reader leave every account consistent, with no failure`() {
        val repository = NeighborsRepository()
        val projection = NeighborsProjection(repository)
        val view = NeighborsView(repository)
        val accounts = (1..4).map { NeighborId(UUID.randomUUID()) }
        accounts.forEach { projection.on(NeighborRegistered(it, "Name $it", "a@example.org", now)) }

        val writers = 16
        val rounds = 4000
        val pool = Executors.newFixedThreadPool(writers + 1)
        val gate = CountDownLatch(1)
        try {
            val work =
                (0 until writers).map { w ->
                    Callable {
                        gate.await()
                        repeat(rounds) { i ->
                            val account = accounts[(w + i) % accounts.size]
                            projection.on(NeighborSuspended(account, now))
                            projection.on(NeighborReinstated(account, now))
                        }
                    }
                } +
                    Callable {
                        gate.await()
                        repeat(rounds) { view.list() }
                    }
            val futures = work.map { pool.submit(it) }
            gate.countDown()
            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }

        val statuses = view.list().associate { it.neighborId to it.status }
        assertEquals(accounts.associateWith { "active" }, accounts.associateWith { statuses[it] })
    }

    @Test
    fun `INV-NBRS-7 - concurrent updates to one account lose none, so every increment lands`() {
        // Each update appends one character. A lost update leaves the string short, so the length is the proof.
        val repository = NeighborsRepository()
        val account = NeighborId(UUID.randomUUID())
        repository.register(NeighborEntry(account, "", "a@example.org", now))
        val writers = 16
        val rounds = 4000
        val pool = Executors.newFixedThreadPool(writers)
        val gate = CountDownLatch(1)
        try {
            val futures =
                (0 until writers).map {
                    pool.submit {
                        gate.await()
                        repeat(rounds) { repository.update(account) { entry -> entry.copy(name = entry.name + "x") } }
                    }
                }
            gate.countDown()
            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
        assertEquals(writers * rounds, repository.find(account)!!.name.length)
    }
}
