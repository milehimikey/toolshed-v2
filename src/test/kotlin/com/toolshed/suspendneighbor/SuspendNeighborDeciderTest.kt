package com.toolshed.suspendneighbor

import com.toolshed.axon.NeighborId
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Pure decider tests: one or more per invariant boundary, no framework and no fixture. */
class SuspendNeighborDeciderTest {
    private val now: Instant = Instant.parse("2026-10-05T15:00:00Z")
    private val ana = NeighborId(UUID.randomUUID())

    private fun state(vararg events: Any): NeighborStatusState {
        val s = NeighborStatusState()
        events.forEach { apply(s, it) }
        return s
    }

    private fun apply(
        s: NeighborStatusState,
        e: Any,
    ) {
        when (e) {
            is NeighborRegistered -> s.evolve(e)
            is NeighborSuspended -> s.evolve(e)
            is NeighborReinstated -> s.evolve(e)
            is NeighborAccountClosed -> s.evolve(e)
            else -> error("no handler for ${e::class.simpleName}")
        }
    }

    private fun registered() = NeighborRegistered(ana, "Ana", "ana@example.com", now)

    private fun decide(s: NeighborStatusState) = SuspendNeighborDecider.decide(SuspendNeighbor(ana), s, now)

    private fun refusal(block: () -> Unit): SuspendNeighborRefused = assertFailsWith<SuspendNeighborRefused>(block = block)

    @Test
    fun `INV-SUS-2 - an active neighbor is suspended, with the time recorded`() {
        val events = decide(state(registered()))
        assertEquals(listOf(NeighborSuspended(ana, now)), events)
    }

    @Test
    fun `INV-SUS-2 - a closed neighbor is refused account-closed`() {
        val e = refusal { decide(state(registered(), NeighborAccountClosed(ana, false, now))) }
        assertEquals("account-closed", e.reason)
        assertEquals("INV-SUS-2", e.invariant)
    }

    @Test
    fun `INV-SUS-2 - an account closed while suspended is closed, not a repeat`() {
        val s = state(registered(), NeighborSuspended(ana, now), NeighborAccountClosed(ana, true, now))
        assertEquals("account-closed", refusal { decide(s) }.reason)
    }

    @Test
    fun `INV-SUS-2 - a neighbor reinstated after a suspension can be suspended again`() {
        val s = state(registered(), NeighborSuspended(ana, now), NeighborReinstated(ana, now))
        assertEquals(1, decide(s).size)
    }

    @Test
    fun `INV-SUS-9 - an already suspended neighbor records nothing`() {
        val s = state(registered(), NeighborSuspended(ana, now))
        assertTrue(decide(s).isEmpty())
    }

    @Test
    fun `INV-SUS-10 - an unregistered neighbor is refused neighbor-not-found`() {
        val e = refusal { decide(state()) }
        assertEquals("neighbor-not-found", e.reason)
        assertEquals("INV-SUS-10", e.invariant)
    }
}
