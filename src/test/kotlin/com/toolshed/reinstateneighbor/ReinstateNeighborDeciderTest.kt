package com.toolshed.reinstateneighbor

import com.toolshed.axon.NeighborId
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReinstateNeighborDeciderTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val n1 = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000a"))
    private val command = ReinstateNeighbor(n1)

    private val suspended = NeighborFacts(registered = true, suspended = true, closed = false)
    private val active = NeighborFacts(registered = true, suspended = false, closed = false)

    private fun refusal(facts: NeighborFacts) =
        assertFailsWith<ReinstateNeighborRejected> {
            ReinstateNeighborDecider.decide(command, facts, now)
        }

    @Test
    fun `INV-REI-2 - given N1 is suspended, when reinstated, then Neighbor Reinstated is recorded`() {
        assertEquals(listOf(NeighborReinstated(n1, now)), ReinstateNeighborDecider.decide(command, suspended, now))
    }

    @Test
    fun `INV-REI-7 - given N1 is already active, when reinstated again, then succeeds and records nothing`() {
        assertEquals(emptyList(), ReinstateNeighborDecider.decide(command, active, now))
    }

    @Test
    fun `INV-REI-7 - given N2 was never suspended, when reinstated, then succeeds and records nothing`() {
        val neverSuspended = NeighborFacts(registered = true, suspended = false, closed = false)
        assertEquals(emptyList(), ReinstateNeighborDecider.decide(command, neverSuspended, now))
    }

    @Test
    fun `INV-REI-2 - given N1 was reinstated and later suspended again, when a late copy arrives, then reinstates again`() {
        // The state after a new suspension is the same as any suspended neighbor's: the late copy is a new request.
        assertEquals(listOf(NeighborReinstated(n1, now)), ReinstateNeighborDecider.decide(command, suspended, now))
    }

    @Test
    fun `INV-REI-2 - given N3 was suspended and then closed, when reinstated, then refused account-closed`() {
        val closedWhileSuspended = NeighborFacts(registered = true, suspended = true, closed = true)
        val rejected = refusal(closedWhileSuspended)
        assertEquals("account-closed", rejected.reason)
        assertEquals("INV-REI-2", rejected.invariant)
        assertEquals("This account is closed.", rejected.message)
    }

    @Test
    fun `INV-REI-2 - given an account closed while active, when reinstated, then refused account-closed`() {
        val closedActive = NeighborFacts(registered = true, suspended = false, closed = true)
        assertEquals("account-closed", refusal(closedActive).reason)
    }

    @Test
    fun `INV-REI-8 - given no account has N9, when reinstated, then refused neighbor-not-found`() {
        val unknown = NeighborFacts(registered = false, suspended = false, closed = false)
        val rejected = refusal(unknown)
        assertEquals("neighbor-not-found", rejected.reason)
        assertEquals("INV-REI-8", rejected.invariant)
        assertEquals("There is no such neighbor.", rejected.message)
    }
}
