package com.toolshed.myaccount

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
 * Writers toggle suspension on shared accounts while a reader keeps asking. Every suspension is followed by its reinstatement,
 * so each account must end active. The counter test below is the lost-update proof a plain HashMap fails.
 */
class MyAccountConcurrencyTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")

    @Test
    fun `INV-MYAC-10 - concurrent writers and a reader leave every account consistent, with no failure`() {
        val repository = MyAccountRepository()
        val projection = MyAccountProjection(repository)
        val view = MyAccountView(repository)
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
                        repeat(rounds) { accounts.forEach { view.get(it) } }
                    }
            val futures = work.map { pool.submit(it) }
            gate.countDown()
            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
        assertEquals(accounts.associateWith { "active" }, accounts.associateWith { view.get(it)!!.status })
    }

    @Test
    fun `INV-MYAC-10 - concurrent updates to one account lose none, so every increment lands`() {
        val repository = MyAccountRepository()
        val account = NeighborId(UUID.randomUUID())
        repository.register(MyAccountEntry(account, "", "a@example.org"))
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
