package com.toolshed.closeaccount

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CloseAccountDeciderTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val n1 = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000b"))
    private val command = CloseAccount(n1)
    private val loan = LoanId(UUID.randomUUID())

    private fun facts(
        registered: Boolean = true,
        suspended: Boolean = false,
        closed: Boolean = false,
        openLoans: Set<LoanId> = emptySet(),
    ) = NeighborAccountFacts(registered, suspended, closed, openLoans)

    @Test
    fun `INV-CLS-2 - given an active account with no tools on loan, when closed, then recorded with whileSuspended no`() {
        assertEquals(listOf(NeighborAccountClosed(n1, false, now)), CloseAccountDecider.decide(command, facts(), now))
    }

    @Test
    fun `INV-CLS-9 - given a suspended account, when closed, then recorded with whileSuspended yes`() {
        assertEquals(listOf(NeighborAccountClosed(n1, true, now)), CloseAccountDecider.decide(command, facts(suspended = true), now))
    }

    @Test
    fun `INV-CLS-9 - given an account reinstated after a suspension, when closed, then whileSuspended no`() {
        // Reinstated: the suspension flag is cleared, so the closure records no suspension.
        assertEquals(listOf(NeighborAccountClosed(n1, false, now)), CloseAccountDecider.decide(command, facts(suspended = false), now))
    }

    @Test
    fun `INV-CLS-2 - given an account already closed, when closed again, then succeeds and records nothing`() {
        assertEquals(emptyList(), CloseAccountDecider.decide(command, facts(closed = true), now))
    }

    @Test
    fun `INV-CLS-7 - given a tool on loan, when closed, then refused tools-on-loan with the doc sentence`() {
        val rejected = assertFailsWith<CloseAccountRejected> { CloseAccountDecider.decide(command, facts(openLoans = setOf(loan)), now) }
        assertEquals("tools-on-loan", rejected.reason)
        assertEquals("INV-CLS-7", rejected.invariant)
        assertEquals("Return all your tools before closing your account.", rejected.message)
    }

    @Test
    fun `INV-CLS-7 - given a loan recorded lost or returned, which is no longer open, when closed, then recorded`() {
        // The state removes a loan on Tool Lost or Tool Returned, so an empty set of open loans closes.
        assertEquals(listOf(NeighborAccountClosed(n1, false, now)), CloseAccountDecider.decide(command, facts(openLoans = emptySet()), now))
    }

    @Test
    fun `INV-CLS-8 - given no account has N1, when closed, then refused neighbor-not-found`() {
        val rejected = assertFailsWith<CloseAccountRejected> { CloseAccountDecider.decide(command, facts(registered = false), now) }
        assertEquals("neighbor-not-found", rejected.reason)
        assertEquals("INV-CLS-8", rejected.invariant)
        assertEquals("There is no such neighbor.", rejected.message)
    }
}
